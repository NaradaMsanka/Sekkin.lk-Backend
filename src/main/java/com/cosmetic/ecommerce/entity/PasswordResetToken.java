package com.cosmetic.ecommerce.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import java.time.Instant;

/** Never returned by an API. Only a SHA-256 digest is persisted, never the bearer token. */
@Entity
@Table(name = "password_reset_tokens", indexes = {
        @Index(name = "idx_password_reset_user", columnList = "user_id"),
        @Index(name = "idx_password_reset_expiry", columnList = "expires_at")
})
@Getter @Setter
public class PasswordResetToken {
    @Id @Column(name = "token_hash", length = 64, nullable = false, updatable = false)
    private String tokenHash;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false, updatable = false)
    private User user;
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;
    @Column(name = "expires_at", nullable = false, updatable = false)
    private Instant expiresAt;
    @Column(name = "used_at")
    private Instant usedAt;
}
