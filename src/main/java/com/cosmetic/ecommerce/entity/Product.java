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

    // Day 26: Discount Price field
    private BigDecimal discountPrice;

    private String brand;

    @Column(nullable = false)
    private Integer stockQuantity;

    private String skinType;

    @Column(columnDefinition = "TEXT")
    private String ingredients;

    private String status;

    @Column(columnDefinition = "TEXT")
    private String imageUrl;

    @Column(columnDefinition = "TEXT")
    private String images;

    @ManyToOne
    @JoinColumn(name = "category_id")
    private Category category;

    // --- Day 26: Helper Methods for Discount Logic ---

    /**
     * වලංගු වට්ටම් මිලක් (discountPrice) තිබේදැයි පරීක්ෂා කරයි.
     * (discountPrice එක 0 ට වඩා වැඩි විය යුතු අතර සාමාන්‍ය price එකට වඩා අඩු විය යුතුය)
     */
    public boolean hasDiscount() {
        return this.discountPrice != null &&
                this.discountPrice.compareTo(BigDecimal.ZERO) > 0 &&
                this.price != null &&
                this.discountPrice.compareTo(this.price) < 0;
    }

    /**
     * Checkout කිරීමේදී ගත යුතු නියම මිල ලබා දෙයි.
     * discountPrice එකක් ඇත්නම් එයත්, නැතිනම් සාමාන්‍ය price එකත් ලබා දෙයි.
     */
    public BigDecimal getEffectivePrice() {
        if (hasDiscount()) {
            return this.discountPrice;
        }
        return this.price;
    }
}