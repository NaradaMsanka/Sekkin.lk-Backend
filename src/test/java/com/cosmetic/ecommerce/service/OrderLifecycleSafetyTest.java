package com.cosmetic.ecommerce.service;

import com.cosmetic.ecommerce.entity.*;
import com.cosmetic.ecommerce.dto.DirectOrderRequestDTO;
import com.cosmetic.ecommerce.repository.*;
import com.fasterxml.jackson.databind.json.JsonMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.server.ResponseStatusException;
import java.math.BigDecimal;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class OrderLifecycleSafetyTest {
    @Mock OrderRepository orders; @Mock ProductRepository products; @Mock UserRepository users; @Mock CartService carts;
    @InjectMocks OrderService service;

    private Order order(String status) {
        Order order = new Order(); order.setId(1L); order.setStatus(status); order.setPaymentMethod("COD");
        order.setPaymentStatus("PENDING_COD"); order.setTotalAmount(1350.0); order.setDeliveryCharge(350.0);
        when(orders.findByIdForUpdate(1L)).thenReturn(Optional.of(order)); return order;
    }
    private Product product(int stock) {
        Product p = new Product(); p.setId(2L); p.setName("Synthetic fixture"); p.setPrice(new BigDecimal("1000")); p.setStockQuantity(stock); return p;
    }

    @Test void checkoutIsAnUnacceptedRequestAndQuoteChangesNeverReserveInventory() {
        Product p = product(3); when(products.findByIdForUpdate(2L)).thenReturn(Optional.of(p));
        DirectOrderRequestDTO request = new DirectOrderRequestDTO(); request.setProductId(2L); request.setQuantity(1); request.setPaymentMethod("COD");
        request.setExpectedTotal(new BigDecimal("1000"));
        ResponseStatusException changed = assertThrows(ResponseStatusException.class, () -> service.createDirectOrder(request, null));
        assertEquals(409, changed.getStatusCode().value()); assertEquals(3, p.getStockQuantity()); verifyNoInteractions(orders);
        request.setExpectedTotal(new BigDecimal("1400")); when(orders.saveAndFlush(any())).thenAnswer(call -> call.getArgument(0));
        Order created = service.createDirectOrder(request, null);
        assertEquals("PENDING", created.getStatus()); assertEquals("PENDING_COD", created.getPaymentStatus());
        assertTrue(created.getInventoryReserved()); assertNull(created.getAcceptedAt()); assertEquals(2, p.getStockQuantity());
    }

    @Test void acceptanceNeedsExplicitConfirmationAndLeavesPaymentPending() {
        Order order = order("PENDING"); order.setInventoryReserved(true); when(orders.save(order)).thenReturn(order);
        assertThrows(ResponseStatusException.class, () -> service.updateOrderStatus(1L, "PROCESSING"));
        assertThrows(ResponseStatusException.class, () -> service.acceptOrder(1L, " ", "admin@example.test"));
        service.acceptOrder(1L, "manual-confirmation-001", "admin@example.test");
        assertEquals("PROCESSING", order.getStatus()); assertEquals("PENDING_COD", order.getPaymentStatus());
        assertNotNull(order.getAcceptedAt()); assertEquals("admin@example.test", order.getAcceptedBy());
        service.acceptOrder(1L, "manual-confirmation-001", "admin@example.test");
        verify(orders, times(1)).save(order);
    }

    @Test void cancellationRestoresOnlyKnownNewReservationExactlyOnce() {
        Order order = order("PENDING"); order.setInventoryReserved(true); Product p = product(3);
        OrderItem item = new OrderItem(); item.setOrder(order); item.setProduct(p); item.setQuantity(2); order.getOrderItems().add(item);
        when(products.findByIdForUpdate(2L)).thenReturn(Optional.of(p)); when(orders.save(order)).thenReturn(order);
        service.updateOrderStatus(1L, "CANCELLED"); service.updateOrderStatus(1L, "CANCELLED");
        assertEquals(5, p.getStockQuantity()); assertFalse(order.getInventoryReserved());
        assertEquals(1350.0, order.getTotalAmount()); assertEquals(350.0, order.getDeliveryCharge());
        verify(products, times(1)).save(p); verify(orders, times(1)).save(order);
    }

    @Test void legacyUnknownReservationIsNeverAssumedOrRestocked() {
        Order order = order("PENDING"); when(orders.save(order)).thenReturn(order);
        assertNull(order.getInventoryReserved()); assertThrows(ResponseStatusException.class, () -> service.acceptOrder(1L, "confirmed", "admin@example.test"));
        service.updateOrderStatus(1L, "CANCELLED");
        assertNull(order.getInventoryReserved()); verifyNoInteractions(products);
    }

    @Test void dispatchedOrdersCannotBeCancelledOrRegressedAndDeliveryDoesNotMeanPaid() {
        Order order = order("SHIPPED"); when(orders.save(order)).thenReturn(order);
        for (String invalid : List.of("CANCELLED", "PROCESSING", "PENDING")) assertThrows(ResponseStatusException.class, () -> service.updateOrderStatus(1L, invalid));
        service.updateOrderStatus(1L, "DELIVERED"); assertEquals("PENDING_COD", order.getPaymentStatus());
        assertThrows(ResponseStatusException.class, () -> service.updateOrderStatus(1L, "CANCELLED")); verifyNoInteractions(products);
    }

    @Test void exactVerifiedCollectionAuditsOperatorAndRepeatIsIdempotent() {
        Order order = order("DELIVERED"); when(orders.save(order)).thenReturn(order);
        assertThrows(ResponseStatusException.class, () -> service.recordCodCollection(1L, new BigDecimal("1000"), "courier-batch-001", "admin@example.test"));
        service.recordCodCollection(1L, new BigDecimal("1350.00"), "courier-batch-001", "admin@example.test");
        assertEquals("PAID", order.getPaymentStatus()); assertNotNull(order.getCodCollectedAt());
        assertEquals("admin@example.test", order.getCodCollectedBy()); assertEquals("courier-batch-001", order.getCodCollectionReference());
        service.recordCodCollection(1L, new BigDecimal("1350"), "courier-batch-001", "other-admin@example.test");
        assertEquals("admin@example.test", order.getCodCollectedBy()); verify(orders, times(1)).save(order);
        assertThrows(ResponseStatusException.class, () -> service.recordCodCollection(1L, new BigDecimal("1350"), "different-reference", "admin@example.test"));
    }

    @Test void failedCollectionNonCodAndUnauditedLegacyPaidCannotBeMarkedPaid() {
        Order order = order("SHIPPED");
        assertThrows(ResponseStatusException.class, () -> service.recordCodCollection(1L, new BigDecimal("1350"), "ref", "admin@example.test"));
        order.setStatus("DELIVERED"); order.setPaymentMethod("CARD");
        assertThrows(ResponseStatusException.class, () -> service.recordCodCollection(1L, new BigDecimal("1350"), "ref", "admin@example.test"));
        order.setPaymentMethod("COD"); order.setPaymentStatus("PAID");
        assertThrows(ResponseStatusException.class, () -> service.recordCodCollection(1L, new BigDecimal("1350"), "ref", "admin@example.test"));
        assertNull(order.getCodCollectedAt()); verify(orders, never()).save(any());
    }

    @Test void extremeExponentsAreRejectedBeforeMoneyExpansion() {
        Order order = order("DELIVERED");
        assertThrows(ResponseStatusException.class, () -> service.recordCodCollection(1L, new BigDecimal("1e100000000"), "ref", "admin@example.test"));
        assertEquals("PENDING_COD", order.getPaymentStatus()); verify(orders, never()).save(any());
    }

    @Test void internalAuditReferencesNeverSerializeToCustomerOrderJson() throws Exception {
        Order order = new Order(); order.setRequestKeyHash("synthetic-key-hash"); order.setRequestFingerprint("synthetic-fingerprint");
        order.setAcceptedBy("internal-operator"); order.setAcceptanceReference("internal-acceptance-evidence");
        order.setCodCollectedBy("internal-operator"); order.setCodCollectionReference("internal-collection-evidence");
        String json = JsonMapper.builder().findAndAddModules().build().writeValueAsString(order);
        for (String field : List.of("requestKeyHash", "requestFingerprint", "acceptedBy", "acceptanceReference", "codCollectedBy", "codCollectionReference", "inventoryReserved")) assertFalse(json.contains(field));
    }

    @Test void valuesThatCannotPreserveStoredCentsAreRejectedBeforeInventoryChanges() {
        Product product = product(2); product.setPrice(new BigDecimal("999999999999000.99"));
        when(products.findByIdForUpdate(2L)).thenReturn(Optional.of(product));
        DirectOrderRequestDTO request = new DirectOrderRequestDTO(); request.setProductId(2L); request.setQuantity(1); request.setPaymentMethod("COD");
        request.setExpectedTotal(new BigDecimal("999999999999350.99"));
        assertEquals(400, assertThrows(ResponseStatusException.class, () -> service.createDirectOrder(request, null)).getStatusCode().value());
        assertEquals(2, product.getStockQuantity()); verify(products, never()).save(any()); verifyNoInteractions(orders);
    }
}
