package com.cosmetic.ecommerce.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import com.fasterxml.jackson.annotation.JsonAnySetter;
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

    // COD only until online payments have verified provider integration.
    @NotBlank(message = "Payment method is required")
    @Pattern(regexp = "(?i)\\s*COD\\s*", message = "Online payments are not enabled. Use cash on delivery.")
    private String paymentMethod;

    private String couponCode;

    // Reject removed card fields and client-supplied payment statuses without retaining their values.
    @JsonAnySetter
    public void rejectUnexpectedField(String field, Object ignoredValue) {
        // Older COD clients sent these unused keys as null. Accept no actual credentials.
        if (ignoredValue == null && java.util.Set.of("cardNumber", "cardExpiry", "cardCvc").contains(field)) return;
        throw new IllegalArgumentException("Unsupported direct-checkout field.");
    }
}
