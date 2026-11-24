package com.cosmetic.ecommerce.security;

import com.cosmetic.ecommerce.dto.DirectOrderRequestDTO;
import com.cosmetic.ecommerce.entity.Order;
import com.cosmetic.ecommerce.entity.Product;
import com.cosmetic.ecommerce.entity.User;
import com.cosmetic.ecommerce.repository.*;
import com.cosmetic.ecommerce.service.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.*;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import java.math.BigDecimal;
import java.util.Optional;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.any;

@ExtendWith(MockitoExtension.class)
class OrderOwnershipSafetyTest {
    @Mock OrderRepository orders;
    @Mock ProductRepository products;
    @Mock UserRepository users;
    @Mock CartService carts;
    @InjectMocks OrderService service;

    private DirectOrderRequestDTO request() {
        Product product = new Product(); product.setId(1L); product.setName("Fixture");
        product.setPrice(new BigDecimal("1000")); product.setStockQuantity(10);
        when(products.findByIdForUpdate(1L)).thenReturn(Optional.of(product));
        when(orders.saveAndFlush(any())).thenAnswer(call -> call.getArgument(0));
        DirectOrderRequestDTO request = new DirectOrderRequestDTO(); request.setProductId(1L);
        request.setQuantity(1); request.setPaymentMethod("COD"); request.setEmail("victim@example.test");
        return request;
    }

    @Test void guestCannotClaimAnAccountBySubmittingItsEmail() {
        DirectOrderRequestDTO request = new DirectOrderRequestDTO(); request.setEmail("victim@example.test");
        for (String email : new String[]{null, "", " ", "anonymousUser"}) {
            assertEquals(HttpStatus.UNAUTHORIZED, assertThrows(ResponseStatusException.class,
                    () -> service.createDirectOrder(request, email)).getStatusCode());
        }
        verifyNoInteractions(users, products, orders, carts);
    }

    @Test void unavailableOrDisabledAccountCannotReserveStockOrCreateAnOrder() {
        DirectOrderRequestDTO request = new DirectOrderRequestDTO(); request.setProductId(1L); request.setPaymentMethod("COD");
        assertEquals(HttpStatus.UNAUTHORIZED, assertThrows(ResponseStatusException.class,
                () -> service.createDirectOrder(request, "missing@example.test")).getStatusCode());
        User disabled = new User(); disabled.setEnabled(false);
        when(users.findByEmailForUpdate("disabled@example.test")).thenReturn(disabled);
        assertEquals(HttpStatus.UNAUTHORIZED, assertThrows(ResponseStatusException.class,
                () -> service.createDirectOrder(request, "disabled@example.test")).getStatusCode());
        verifyNoInteractions(products, orders, carts);
    }

    @Test void signedInOrderUsesPrincipalRatherThanContactEmail() {
        User principal = new User(); principal.setEmail("owner@example.test");
        when(users.findByEmailForUpdate(principal.getEmail())).thenReturn(principal);
        Order result = service.createDirectOrder(request(), principal.getEmail());
        assertSame(principal, result.getUser()); assertEquals("victim@example.test", result.getCustomerEmail());
        assertEquals(400.0, result.getDeliveryCharge()); assertEquals(1400.0, result.getTotalAmount());
        verify(users, never()).findByEmailForUpdate("victim@example.test");
    }

    @Test void ownerCanReadStoredAmountsWithoutRepricing() {
        User owner = new User(); owner.setEmail("owner@example.test");
        Order historical = new Order(); historical.setUser(owner); historical.setDeliveryCharge(0.0); historical.setTotalAmount(1000.0);
        when(orders.findById(1L)).thenReturn(Optional.of(historical));
        assertSame(historical, service.getOrderById(1L, owner.getEmail()));
        assertEquals(0.0, historical.getDeliveryCharge()); assertEquals(1000.0, historical.getTotalAmount()); verifyNoInteractions(products);
    }

    @Test void otherCustomersAndGuestsCannotReadAnOrder() {
        User owner = new User(); owner.setEmail("owner@example.test");
        Order order = new Order(); order.setUser(owner); when(orders.findById(1L)).thenReturn(Optional.of(order));
        for (String requester : new String[]{null, "", "other@example.test"}) {
            assertEquals(HttpStatus.FORBIDDEN, assertThrows(ResponseStatusException.class,
                    () -> service.getOrderById(1L, requester)).getStatusCode());
        }
        order.setUser(null);
        assertEquals(HttpStatus.FORBIDDEN, assertThrows(ResponseStatusException.class,
                () -> service.getOrderById(1L, "owner@example.test")).getStatusCode());
    }

    @ParameterizedTest @ValueSource(strings = {"PAID", "KOKO_APPROVED", "REFUNDED", "COMPLETED", "invented"})
    void financialAndUnknownValuesCannotBecomeFulfilmentStatus(String value) {
        Order order = new Order(); order.setStatus("PENDING"); order.setPaymentStatus("PENDING_COD");
        assertEquals(HttpStatus.BAD_REQUEST, assertThrows(ResponseStatusException.class,
                () -> service.updateOrderStatus(1L, value)).getStatusCode());
        assertEquals("PENDING", order.getStatus()); assertEquals("PENDING_COD", order.getPaymentStatus());
        verify(orders, never()).save(any());
    }

    @ParameterizedTest @ValueSource(strings = {"PENDING", "PROCESSING", "SHIPPED", "DELIVERED", "CANCELLED"})
    void supportedFulfilmentStatusCannotChangePaymentOrTotals(String status) {
        Order order = new Order(); order.setDeliveryCharge(0.0); order.setTotalAmount(1000.0); order.setPaymentStatus("PENDING_COD");
        when(orders.findByIdForUpdate(1L)).thenReturn(Optional.of(order));
        if (!"PENDING".equals(status)) when(orders.save(order)).thenReturn(order);
        order.setStatus("SHIPPED".equals(status) ? "PROCESSING" : "DELIVERED".equals(status) ? "SHIPPED" : "PENDING");
        order.setInventoryReserved(true);
        if ("PROCESSING".equals(status)) service.acceptOrder(1L, "confirmation-reference", "admin@example.test");
        else service.updateOrderStatus(1L, status);
        assertEquals(status, order.getStatus()); assertEquals("PENDING_COD", order.getPaymentStatus());
        assertEquals(0.0, order.getDeliveryCharge()); assertEquals(1000.0, order.getTotalAmount()); verifyNoInteractions(products);
    }
}
