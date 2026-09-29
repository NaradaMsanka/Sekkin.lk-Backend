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

    public String generateToken(String email) {
        return Jwts.builder()
                .setSubject(email)
                .setIssuedAt(new Date())
                .setExpiration(new Date(System.currentTimeMillis() + 1000 * 60 * 60 * 10)) // පැය 10ක් වැඩ
                .signWith(key, SignatureAlgorithm.HS256)
                .compact();
    }
    // Token එකෙන් Email එක වෙන් කරලා ගන්න
    public String extractEmail(String token) {
        return Jwts.parserBuilder().setSigningKey(key).build().parseClaimsJws(token).getBody().getSubject();
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