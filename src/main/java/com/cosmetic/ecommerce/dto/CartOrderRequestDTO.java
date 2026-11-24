package com.cosmetic.ecommerce.dto;

import jakarta.validation.constraints.*;
import lombok.Data;
import java.math.BigDecimal;

@Data
public class CartOrderRequestDTO {
    @NotBlank @Size(max = 2000) private String shippingAddress;
    @NotBlank @Size(max = 40) private String customerPhone;
    @Size(max = 160) private String customerName;
    @Email @Size(max = 254) private String customerEmail;
    @Size(max = 40) private String postalCode;
    @Size(max = 80) private String couponCode;
    @NotNull @DecimalMin("0.01") @DecimalMax("999999999999999.99") @Digits(integer = 15, fraction = 2) private BigDecimal expectedTotal;
    @NotNull @Size(min = 1, max = 200)
    private java.util.List<@jakarta.validation.Valid CartLine> cartItems;
    public record CartLine(@NotNull @Min(1) Long productId, @NotNull @Min(1) Integer quantity) {}
    @com.fasterxml.jackson.annotation.JsonAnySetter
    public void rejectUnexpectedField(String field, Object ignored) {
        throw new IllegalArgumentException("Unsupported checkout field");
    }
}
