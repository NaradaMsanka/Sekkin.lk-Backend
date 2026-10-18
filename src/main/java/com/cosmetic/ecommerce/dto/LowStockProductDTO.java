package com.cosmetic.ecommerce.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import java.math.BigDecimal;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class LowStockProductDTO {
    private Long id;
    private String name;
    private String brand;
    private Integer stockQuantity;
    private BigDecimal price;
}