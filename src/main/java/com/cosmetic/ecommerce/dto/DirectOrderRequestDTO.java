package com.cosmetic.ecommerce.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class DirectOrderRequestDTO {
    @NotNull(message = "Product ID is required")
    private Long productId;

    private Integer quantity = 1;

    @NotBlank(message = "Customer name is required")
    private String name;

    @NotBlank(message = "Phone number is required")
    private String phoneNumber;

    @NotBlank(message = "Shipping address is required")
    private String address;

    private String postalCode;

    @NotBlank(message = "Email is required")
    private String email;

    // "CARD", "KOKO", "COD"
    @NotBlank(message = "Payment method is required")
    private String paymentMethod;

    private String couponCode;

    // Optional simulated payment details
    private String cardNumber;
    private String cardExpiry;
    private String cardCvc;
}
