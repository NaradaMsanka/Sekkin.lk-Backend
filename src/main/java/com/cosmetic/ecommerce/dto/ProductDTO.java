package com.cosmetic.ecommerce.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import java.math.BigDecimal;

@Data
public class ProductDTO {
    @NotBlank(message = "Name cannot be empty")
    private String name;

    private String description;

    @NotNull(message = "Price is required")
    private BigDecimal price;

    private BigDecimal discountPrice;
    private String brand;

    @NotNull(message = "Stock is required")
    private Integer stockQuantity;

    private String skinType;
    private String ingredients;
    private String status;
    private String imageUrl;
    private String images;
}