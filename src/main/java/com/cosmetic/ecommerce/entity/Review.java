package com.cosmetic.ecommerce.entity;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(name = "reviews")
@Data
@NoArgsConstructor
// Bug Fix: @AllArgsConstructor removed — it overrides field default `createdAt = LocalDateTime.now()` with null.
// @PrePersist is now used to reliably set the timestamp before DB insert.
public class Review {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private int rating; // 1 සිට 5 දක්වා

    @Column(nullable = false, length = 1000)
    private String comment;

    private LocalDateTime createdAt;

    @PrePersist
    public void setCreatedAtOnCreate() {
        if (this.createdAt == null) {
            this.createdAt = LocalDateTime.now();
        }
    }

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "product_id", nullable = false)
    private Product product;
}