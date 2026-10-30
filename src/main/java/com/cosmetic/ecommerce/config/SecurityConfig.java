package com.cosmetic.ecommerce.config;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.List;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity
public class SecurityConfig {

    @Autowired
    private JwtFilter jwtFilter; // අපි හදපු Guard ව මෙතනට ගේනවා

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                // CORS configuration එක මෙහිදී නිවැරදිව Bind කර ඇත
                .cors(cors -> cors.configurationSource(corsConfigurationSource()))
                .csrf(AbstractHttpConfigurer::disable)
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/api/auth/**").permitAll() // Login/Register වෙන්න Token ඕනේ නැහැ
                        .requestMatchers("/api/products/**").permitAll() // Products බලන්නත් Token ඕනේ නැහැ
                        .requestMatchers("/uploads/**").permitAll() // Product images බලන්න Token ඕනේ නැහැ
                        .requestMatchers("GET", "/api/categories/**").permitAll() // Categories view - public
                        .requestMatchers("/api/orders/direct").permitAll() // Direct Instant Checkout without Cart
                        .requestMatchers("/api/admin/**").hasAuthority("ADMIN") // Bug Fix: Admin routes explicitly locked down
                        .anyRequest().authenticated() // හැබැයි අනිත් ඕනෙම දේකට Token එක අනිවාර්යයි!
                )
                .addFilterBefore(jwtFilter, UsernamePasswordAuthenticationFilter.class); // අපේ Guard ව Filter එකක් විදිහට දානවා

        return http.build();
    }

    @Bean
    public UrlBasedCorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration config = new CorsConfiguration();

        // Local development origins - localhost all common ports
        config.setAllowedOriginPatterns(List.of(
                "http://localhost:*",        // All localhost ports (5173, 3000, 5174, etc.)
                "http://127.0.0.1:*",        // 127.0.0.1 all ports
                "https://*.netlify.app",     // Netlify hosted frontend
                "https://*.vercel.app",      // Vercel hosted frontend
                "https://*.onrender.com"     // Render hosted frontend
        ));

        config.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        config.setAllowedHeaders(List.of("*"));
        config.setExposedHeaders(List.of("Authorization"));
        config.setAllowCredentials(true);
        config.setMaxAge(3600L); // Preflight cache - 1 hour

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);
        return source;
    }
}