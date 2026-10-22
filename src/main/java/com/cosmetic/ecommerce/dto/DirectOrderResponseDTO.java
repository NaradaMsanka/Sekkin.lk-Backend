package com.cosmetic.ecommerce.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DirectOrderResponseDTO {
    private Long orderId;
    private String customerName;
    private String customerPhone;
    private String customerEmail;
    private String shippingAddress;
    private String postalCode;
    private String paymentMethod;
    private String paymentStatus;
    private Double deliveryCharge;
    private Double subtotal;
    private Double totalAmount;
    private Double discountAmount;
    private String status;
    private LocalDateTime orderDate;
    private String productName;
    private Integer quantity;
    private Double unitPrice;
    private String message;
}
