package com.cosmetic.ecommerce.dto;

import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import java.util.Set;

public record PromotionRequest(@NotBlank @Size(max = 80) String code,
        @NotNull @DecimalMin("0.01") @DecimalMax("100.00") @Digits(integer = 3, fraction = 2) BigDecimal percentage,
        @NotNull Boolean active,
        @NotEmpty @Size(max = 200) Set<@NotNull @Min(1) Long> productIds) {}
