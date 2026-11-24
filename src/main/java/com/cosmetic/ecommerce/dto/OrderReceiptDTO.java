package com.cosmetic.ecommerce.dto;

import com.cosmetic.ecommerce.entity.Order;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;

public record OrderReceiptDTO(Long id, Long orderId, String shippingAddress, String customerPhone, Double totalAmount,
        Double deliveryCharge, Double discountAmount, String couponCode, String paymentMethod,
        String paymentStatus, String status, LocalDateTime orderDate, Double subtotal,
        Double couponDiscount, java.math.BigDecimal couponPercentage, String message) {
    public static OrderReceiptDTO from(Order order) {
        BigDecimal subtotal = order.getOrderItems().stream()
                .map(item -> BigDecimal.valueOf(item.getPrice()).multiply(BigDecimal.valueOf(item.getQuantity())))
                .reduce(BigDecimal.ZERO, BigDecimal::add).setScale(2, RoundingMode.HALF_UP);
        BigDecimal coupon = subtotal.add(BigDecimal.valueOf(order.getDeliveryCharge()))
                .subtract(BigDecimal.valueOf(order.getTotalAmount())).max(BigDecimal.ZERO).setScale(2, RoundingMode.HALF_UP);
        return new OrderReceiptDTO(order.getId(), order.getId(), order.getShippingAddress(), order.getCustomerPhone(), order.getTotalAmount(),
                order.getDeliveryCharge(), order.getDiscountAmount(), order.getCouponCode(), order.getPaymentMethod(),
                order.getPaymentStatus(), order.getStatus(), order.getOrderDate(), subtotal.doubleValue(),
                order.getCouponDiscount() == null ? coupon.doubleValue() : order.getCouponDiscount(), order.getCouponPercentage(),
                "Order request received. Acceptance requires business confirmation. This receipt is not proof of payment.");
    }
}
