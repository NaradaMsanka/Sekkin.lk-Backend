package com.cosmetic.ecommerce.entity;

import jakarta.persistence.*;
import lombok.*;
import java.math.BigDecimal;

@Entity
@Table(name = "products")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class Product {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String name;

    @Column(length = 1000)
    private String description;

    @Column(nullable = false)
    private BigDecimal price;

    private BigDecimal discountPrice;

    private String brand;

    @Column(nullable = false)
    private Integer stockQuantity;

    private String skinType;

    @Column(columnDefinition = "TEXT")
    private String ingredients;

    private String status;
    @ManyToOne
    @JoinColumn(name = "category_id")
    private Category category;
}