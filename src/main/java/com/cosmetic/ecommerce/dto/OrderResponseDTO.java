package com.cosmetic.ecommerce.dto;

import java.time.LocalDateTime;

public class OrderResponseDTO {
    private Long id;
    private String shippingAddress;
    private Double totalAmount;
    private String status;
    private LocalDateTime orderDate;
    private Double deliveryCharge;
    private String couponCode;
    private java.math.BigDecimal couponPercentage;
    private Double couponDiscount;

    public OrderResponseDTO(Long id, String shippingAddress, Double totalAmount, String status, LocalDateTime orderDate) {
        this.id = id;
        this.shippingAddress = shippingAddress;
        this.totalAmount = totalAmount;
        this.status = status;
        this.orderDate = orderDate;
    }

    // Getters
    public Long getId() { return id; }
    public String getShippingAddress() { return shippingAddress; }
    public Double getTotalAmount() { return totalAmount; }
    public String getStatus() { return status; }
    public LocalDateTime getOrderDate() { return orderDate; }
    public Double getDeliveryCharge() { return deliveryCharge; }
    public void setDeliveryCharge(Double deliveryCharge) { this.deliveryCharge = deliveryCharge; }
    public String getCouponCode() { return couponCode; }
    public void setCouponCode(String value) { couponCode = value; }
    public java.math.BigDecimal getCouponPercentage() { return couponPercentage; }
    public void setCouponPercentage(java.math.BigDecimal value) { couponPercentage = value; }
    public Double getCouponDiscount() { return couponDiscount; }
    public void setCouponDiscount(Double value) { couponDiscount = value; }
}
