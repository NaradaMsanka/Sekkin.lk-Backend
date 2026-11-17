package com.cosmetic.ecommerce.config;

import com.cosmetic.ecommerce.entity.User;
import com.cosmetic.ecommerce.repository.UserRepository;
import com.cosmetic.ecommerce.util.JwtUtil;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import java.io.IOException;
import java.util.List;

@Component
public class JwtFilter extends OncePerRequestFilter {
    private final JwtUtil jwtUtil;
    private final UserRepository users;

    public JwtFilter(JwtUtil jwtUtil, UserRepository users) {
        this.jwtUtil = jwtUtil;
        this.users = users;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String header = request.getHeader("Authorization");
        if (header != null && header.startsWith("Bearer ")) {
            String token = header.substring(7).trim();
            if (token.startsWith("\"") && token.endsWith("\"") && token.length() > 2) token = token.substring(1, token.length() - 1);
            SecurityContextHolder.clearContext();
            if (jwtUtil.validateToken(token)) {
                String email = jwtUtil.extractEmail(token);
                if (email != null && !email.isBlank()) {
                    final User user;
                    try { user = users.findByEmail(email); }
                    catch (RuntimeException unavailable) {
                        response.setStatus(HttpServletResponse.SC_SERVICE_UNAVAILABLE);
                        response.setContentType("application/json");
                        response.getWriter().write("{\"error\":\"Authentication temporarily unavailable\"}");
                        return;
                    }
                    // Current database authority revokes removed admin privileges on the next request.
                    Long version = jwtUtil.extractSessionVersion(token);
                    if (user != null && user.isEnabled() && version != null && version == user.getSessionVersion()
                            && ("USER".equals(user.getRole()) || "ADMIN".equals(user.getRole()))) {
                        var authentication = new UsernamePasswordAuthenticationToken(user.getEmail(), null,
                                List.of(new SimpleGrantedAuthority(user.getRole())));
                        authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
                        var context = SecurityContextHolder.createEmptyContext();
                        context.setAuthentication(authentication);
                        SecurityContextHolder.setContext(context);
                    }
                }
            }
        }
        // No token, subject, role, customer details or exception messages are logged.
        chain.doFilter(request, response);
    }
}
