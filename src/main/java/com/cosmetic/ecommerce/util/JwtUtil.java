package com.cosmetic.ecommerce.util;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.security.Keys;
import org.springframework.stereotype.Component;
import org.springframework.beans.factory.annotation.Value;

import java.nio.charset.StandardCharsets;
import javax.crypto.SecretKey;
import java.util.Date;

@Component
public class JwtUtil {

    private final SecretKey key;

    public JwtUtil(@Value("${security.jwt.secret}") String secret) {
        if (secret == null || secret.isBlank() || secret.getBytes(StandardCharsets.UTF_8).length < 32) {
            throw new IllegalArgumentException("A server-only JWT_SECRET of at least 32 bytes is required.");
        }
        this.key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
    }

    // Token expiry — 7 days (7 × 24 × 60 × 60 × 1000 ms)
    private static final long TOKEN_EXPIRY_MS = 7L * 24 * 60 * 60 * 1000;

    public String generateToken(String email, String role) {
        return generateToken(email, role, 0);
    }

    public String generateToken(String email, String role, long sessionVersion) {
        if (sessionVersion < 0) throw new IllegalArgumentException("Invalid session version");
        if (role == null || role.trim().isEmpty()) {
            role = "USER";
        }
        return Jwts.builder()
                .setSubject(email)
                .claim("role", role)
                .claim("sessionVersion", sessionVersion)
                .setIssuedAt(new Date())
                .setExpiration(new Date(System.currentTimeMillis() + TOKEN_EXPIRY_MS))
                .signWith(key, SignatureAlgorithm.HS256)
                .compact();
    }

    public String extractEmail(String token) {
        try {
            return Jwts.parser().verifyWith(key).build().parseSignedClaims(token).getPayload().getSubject();
        } catch (RuntimeException invalidClaims) {
            return null;
        }
    }

    public String extractRole(String token) {
        try {
            String role = Jwts.parser().verifyWith(key).build().parseSignedClaims(token).getPayload().get("role", String.class);
            return (role != null && !role.trim().isEmpty()) ? role : "USER";
        } catch (Exception e) {
            return "USER";
        }
    }

    public boolean validateToken(String token) {
        try {
            var claims = Jwts.parser().verifyWith(key).build().parseSignedClaims(token).getPayload();
            return claims.getExpiration() != null && claims.getSubject() != null && !claims.getSubject().isBlank()
                    && sessionVersion(claims.get("sessionVersion")) != null;
        } catch (Exception e) {
            // Invalid tokens fail closed; do not log token/claim exception details.
            return false;
        }
    }

    public Long extractSessionVersion(String token) {
        try {
            var claims = Jwts.parser().verifyWith(key).build().parseSignedClaims(token).getPayload();
            return sessionVersion(claims.get("sessionVersion"));
        } catch (RuntimeException invalidClaims) {
            return null;
        }
    }

    private Long sessionVersion(Object value) {
        // Strings/fractions/negative values are not valid versions. Legacy tokens without this claim fail closed.
        if (!(value instanceof Integer || value instanceof Long)) return null;
        long version = ((Number) value).longValue();
        return version >= 0 ? version : null;
    }
}
