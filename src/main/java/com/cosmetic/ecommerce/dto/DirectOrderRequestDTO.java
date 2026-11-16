package com.cosmetic.ecommerce.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import com.fasterxml.jackson.annotation.JsonAnySetter;
import lombok.Data;

@Data
public class DirectOrderRequestDTO {
    @NotNull(message = "Product ID is required")
    @jakarta.validation.constraints.Min(1)
    private Long productId;

    @jakarta.validation.constraints.Min(1) private Integer quantity = 1;

    @NotBlank(message = "Customer name is required")
    @jakarta.validation.constraints.Size(max = 160) private String name;

    @NotBlank(message = "Phone number is required")
    @jakarta.validation.constraints.Size(max = 40) private String phoneNumber;

    @NotBlank(message = "Shipping address is required")
    @jakarta.validation.constraints.Size(max = 2000) private String address;

    @jakarta.validation.constraints.Size(max = 40) private String postalCode;

    @NotBlank(message = "Email is required")
    @jakarta.validation.constraints.Size(max = 254) private String email;

    // COD only until online payments have verified provider integration.
    @NotBlank(message = "Payment method is required")
    @jakarta.validation.constraints.Size(max = 16)
    @Pattern(regexp = "(?i)\\s*COD\\s*", message = "Online payments are not enabled. Use cash on delivery.")
    private String paymentMethod;

    @jakarta.validation.constraints.Size(max = 80) private String couponCode;

    // Consent check only. The server still calculates every price and delivery charge.
    @NotNull(message = "Displayed order total is required")
    @jakarta.validation.constraints.DecimalMin("0.01")
    @jakarta.validation.constraints.Digits(integer = 15, fraction = 2)
    @jakarta.validation.constraints.DecimalMax("999999999999999.99")
    private java.math.BigDecimal expectedTotal;

    // Reject removed card fields and client-supplied payment statuses without retaining their values.
    @JsonAnySetter
    public void rejectUnexpectedField(String field, Object ignoredValue) {
        // Older COD clients sent these unused keys as null. Accept no actual credentials.
        if (ignoredValue == null && java.util.Set.of("cardNumber", "cardExpiry", "cardCvc").contains(field)) return;
        throw new IllegalArgumentException("Unsupported direct-checkout field.");
    }
}
