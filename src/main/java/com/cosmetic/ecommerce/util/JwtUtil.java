package com.cosmetic.ecommerce.util;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.security.Keys;
import org.springframework.stereotype.Component;

import java.security.Key;
import java.util.Date;

@Component
public class JwtUtil {

    // මේක තමයි අපේ රහස්‍ය යතුර. (අකුරු 32කට වඩා තියෙන්න ඕනේ)
    private static final String SECRET = "SekkinLkCosmeticsSuperSecretKeyForJwtAuthentication2026";
    private static final Key key = Keys.hmacShaKeyFor(SECRET.getBytes());

    // Token expiry — 7 days (7 × 24 × 60 × 60 × 1000 ms)
    private static final long TOKEN_EXPIRY_MS = 7L * 24 * 60 * 60 * 1000;

    // Update 1: Token එක හදද්දී Role එකත් එකතු කරනවා
    public String generateToken(String email, String role) {
        return Jwts.builder()
                .setSubject(email)
                .claim("role", role) // Token එක ඇතුළට Role එක දාන තැන
                .setIssuedAt(new Date())
                .setExpiration(new Date(System.currentTimeMillis() + TOKEN_EXPIRY_MS)) // 7 days valid
                .signWith(key, SignatureAlgorithm.HS256)
                .compact();
    }

    // Token එකෙන් Email එක වෙන් කරලා ගන්න
    public String extractEmail(String token) {
        return Jwts.parserBuilder().setSigningKey(key).build().parseClaimsJws(token).getBody().getSubject();
    }

    // Update 2: Token එකෙන් Role එක වෙන් කරලා ගන්න (අලුතෙන් එකතු කළ කොටස)
    public String extractRole(String token) {
        return Jwts.parserBuilder().setSigningKey(key).build().parseClaimsJws(token).getBody().get("role", String.class);
    }

    // Token එක නිවැරදිද සහ කල් ඉකුත් වෙලා නැද්ද කියලා බලන්න
    public boolean validateToken(String token) {
        try {
            Jwts.parserBuilder().setSigningKey(key).build().parseClaimsJws(token);
            return true;
        } catch (Exception e) {
            return false;
        }
    }
}