package com.cosmetic.ecommerce.util;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.security.Keys;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.Key;
import java.util.Date;

@Component
public class JwtUtil {

    private static final String SECRET = "SekkinLkCosmeticsSuperSecretKeyForJwtAuthentication2026";
    private static final Key key = Keys.hmacShaKeyFor(SECRET.getBytes(StandardCharsets.UTF_8));

    // Token expiry — 7 days (7 × 24 × 60 × 60 × 1000 ms)
    private static final long TOKEN_EXPIRY_MS = 7L * 24 * 60 * 60 * 1000;

    public String generateToken(String email, String role) {
        if (role == null || role.trim().isEmpty()) {
            role = "USER";
        }
        return Jwts.builder()
                .setSubject(email)
                .claim("role", role)
                .setIssuedAt(new Date())
                .setExpiration(new Date(System.currentTimeMillis() + TOKEN_EXPIRY_MS))
                .signWith(key, SignatureAlgorithm.HS256)
                .compact();
    }

    public String extractEmail(String token) {
        return Jwts.parserBuilder().setSigningKey(key).build().parseClaimsJws(token).getBody().getSubject();
    }

    public String extractRole(String token) {
        try {
            String role = Jwts.parserBuilder().setSigningKey(key).build().parseClaimsJws(token).getBody().get("role", String.class);
            return (role != null && !role.trim().isEmpty()) ? role : "USER";
        } catch (Exception e) {
            return "USER";
        }
    }

    public boolean validateToken(String token) {
        try {
            Jwts.parserBuilder().setSigningKey(key).build().parseClaimsJws(token);
            return true;
        } catch (Exception e) {
            System.out.println("validateToken failed: " + e.getClass().getSimpleName() + " - " + e.getMessage());
            return false;
        }
    }
}
