package com.cosmetic.ecommerce.dto;

import com.cosmetic.ecommerce.entity.Order;
import java.time.LocalDateTime;

public record AdminOrderDTO(Long id, String customerName, String customerEmail, String customerPhone,
        String shippingAddress, Double totalAmount, Double deliveryCharge, String status,
        String paymentMethod, String paymentStatus, LocalDateTime orderDate, Boolean inventoryReserved,
        LocalDateTime acceptedAt, String acceptedBy, String acceptanceReference,
        LocalDateTime codCollectedAt, String codCollectedBy, String codCollectionReference,
        String couponCode, java.math.BigDecimal couponPercentage, Double couponDiscount) {
    public static AdminOrderDTO from(Order order) {
        return new AdminOrderDTO(order.getId(), order.getCustomerName(),
                order.getCustomerEmail() != null ? order.getCustomerEmail() : order.getUser() == null ? null : order.getUser().getEmail(),
                order.getCustomerPhone(), order.getShippingAddress(), order.getTotalAmount(), order.getDeliveryCharge(),
                order.getStatus(), order.getPaymentMethod(), order.getPaymentStatus(), order.getOrderDate(), order.getInventoryReserved(),
                order.getAcceptedAt(), order.getAcceptedBy(), order.getAcceptanceReference(),
                order.getCodCollectedAt(), order.getCodCollectedBy(), order.getCodCollectionReference(),
                order.getCouponCode(), order.getCouponPercentage(), order.getCouponDiscount());
    }
}
