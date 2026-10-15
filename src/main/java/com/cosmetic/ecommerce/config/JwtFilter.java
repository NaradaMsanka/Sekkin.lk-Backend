package com.cosmetic.ecommerce.config;

import com.cosmetic.ecommerce.util.JwtUtil;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import java.util.Collections;

import java.io.IOException;

@Component
public class JwtFilter extends OncePerRequestFilter {

    @Autowired
    private JwtUtil jwtUtil;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain) throws ServletException, IOException {
        String authHeader = request.getHeader("Authorization");

        // Request එකේ Token එකක් තියෙනවද බලනවා (Token එක පටන් ගන්නේ "Bearer " කියලා)
        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            String token = authHeader.substring(7); // "Bearer " කෑල්ල අයින් කරලා Token එක විතරක් ගන්නවා

            try {
                // ටෝකන් එක නිවැරදිද නැද්ද යන්න කන්සෝල් එකේ බලාගැනීමට
                boolean isValid = jwtUtil.validateToken(token);
                System.out.println("Auth Header received: " + authHeader);
                System.out.println("Is Token Valid: " + isValid);

                if (isValid) {
                    String email = jwtUtil.extractEmail(token);
                    String role = jwtUtil.extractRole(token); // Token එකෙන් Role එක ගන්නවා

                    System.out.println("Extracted Email: " + email);
                    System.out.println("Extracted Role: " + role);

                    // User ගේ Role එක Spring Security එකට තේරෙන විදිහට හදනවා
                    UsernamePasswordAuthenticationToken authToken = new UsernamePasswordAuthenticationToken(
                            email, null, Collections.singletonList(new SimpleGrantedAuthority(role)));

                    SecurityContextHolder.getContext().setAuthentication(authToken);
                }
            } catch (Exception e) {
                System.out.println("JWT Filtering Error: " + e.getMessage());
            }
        } else {
            System.out.println("Authorization Header missing or does not start with Bearer for URI: " + request.getRequestURI());
        }

        filterChain.doFilter(request, response); // ඊළඟ පියවරට යවනවා
    }
}