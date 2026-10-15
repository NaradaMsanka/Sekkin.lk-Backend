package com.cosmetic.ecommerce.dto;

import java.time.LocalDateTime;

public class OrderResponseDTO {
    private Long id;
    private String shippingAddress;
    private Double totalAmount;
    private String status;
    private LocalDateTime orderDate;

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
}