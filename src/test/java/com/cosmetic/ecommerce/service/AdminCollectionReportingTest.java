package com.cosmetic.ecommerce.service;

import com.cosmetic.ecommerce.entity.Order;
import com.cosmetic.ecommerce.repository.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import java.time.LocalDateTime;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AdminCollectionReportingTest {
    @Mock OrderRepository orders; @Mock ProductRepository products; @Mock UserRepository users;
    @InjectMocks AdminService admin;

    @Test void revenueCountsOnlyAuditedCodCollectionsWithoutRewritingHistoricalAmounts() {
        Order pending = order("PENDING_COD", 1350.0), deliveredUncollected = order("PENDING_COD", 2350.0);
        deliveredUncollected.setStatus("DELIVERED");
        Order simulatedLegacyPaid = order("PAID", 9000.0), verified = order("PAID", 3350.0);
        verified.setCodCollectedAt(LocalDateTime.now()); verified.setCodCollectedBy("admin@example.test"); verified.setCodCollectionReference("reconciliation-001");
        when(orders.findAll()).thenReturn(List.of(pending, deliveredUncollected, simulatedLegacyPaid, verified));
        when(products.findByStockQuantityLessThanEqual(10)).thenReturn(List.of()); when(orders.findTop5ByOrderByOrderDateDesc()).thenReturn(List.of());
        assertEquals(3350.0, admin.getDashboardSummary().getTotalRevenue());
        assertEquals(9000.0, simulatedLegacyPaid.getTotalAmount()); assertNull(simulatedLegacyPaid.getCodCollectedAt());
        verify(orders, never()).save(any());
    }

    private Order order(String paymentStatus, double total) {
        Order order = new Order(); order.setStatus("PENDING"); order.setPaymentMethod("COD"); order.setPaymentStatus(paymentStatus);
        order.setDeliveryCharge(350.0); order.setTotalAmount(total); return order;
    }
}
