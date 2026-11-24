package com.cosmetic.ecommerce.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import java.math.BigDecimal;
import java.util.LinkedHashSet;
import java.util.Set;

@Entity
@Table(name = "promotions", uniqueConstraints = @UniqueConstraint(name = "uk_promotions_code", columnNames = "code"))
@Getter @Setter
public class Promotion {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @Column(nullable = false, length = 40) private String code;
    @Column(nullable = false, precision = 5, scale = 2) private BigDecimal percentage;
    @Column(nullable = false) private boolean active = true;
    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "promotion_products", joinColumns = @JoinColumn(name = "promotion_id"),
            uniqueConstraints = @UniqueConstraint(name = "uk_promotion_product", columnNames = {"promotion_id", "product_id"}))
    @Column(name = "product_id", nullable = false)
    private Set<Long> productIds = new LinkedHashSet<>();
}
