package com.cosmetic.ecommerce.config;

import com.cosmetic.ecommerce.security.RequestThrottle;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.web.filter.OncePerRequestFilter;
import java.io.IOException;
import java.time.Duration;

public class AbuseRateLimitFilter extends OncePerRequestFilter {
    private final RequestThrottle throttle;
    public AbuseRateLimitFilter(RequestThrottle throttle) { this.throttle = throttle; }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        if ("POST".equals(request.getMethod())) {
            String path = request.getServletPath();
            if (path.isEmpty()) path = request.getRequestURI();
            String scope = null;
            int limit = 30;
            Duration window = Duration.ofMinutes(5);
            if ("/api/auth/login".equals(path)) scope = "login-ip";
            else if ("/api/auth/forgot-password".equals(path)) {
                scope = "forgot-password-ip"; limit = 10; window = Duration.ofMinutes(15);
            } else if ("/api/auth/reset-password".equals(path)) {
                scope = "reset-password-ip"; limit = 20; window = Duration.ofMinutes(15);
            }
            else if ("/api/auth/register".equals(path)) {
                scope = "register-ip"; limit = 10; window = Duration.ofHours(1);
            } else if ("/api/orders/direct".equals(path) || "/api/orders".equals(path)) {
                scope = "orders-ip"; window = Duration.ofMinutes(1);
            }
            if (scope != null) {
                // Never trust a caller-supplied X-Forwarded-For. Verify proxy attribution in staging.
                long retry = throttle.attempt(scope, request.getRemoteAddr(), limit, window);
                if (retry > 0) {
                    response.setStatus(429);
                    response.setHeader("Retry-After", Long.toString(retry));
                    response.setContentType("application/json");
                    response.getWriter().write("{\"error\":\"Too many requests; try again later\"}");
                    return;
                }
            }
        }
        chain.doFilter(request, response);
    }
}
