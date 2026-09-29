package com.cosmetic.ecommerce.config;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

@Configuration
@EnableWebSecurity
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
                .csrf(AbstractHttpConfigurer::disable)
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/api/auth/**").permitAll() // Login/Register වෙන්න Token ඕනේ නැහැ
                        .requestMatchers("/api/products/**").permitAll() // Products බලන්නත් Token ඕනේ නැහැ
                        .anyRequest().authenticated() // හැබැයි අනිත් ඕනෙම දේකට Token එක අනිවාර්යයි!
                )
                .addFilterBefore(jwtFilter, UsernamePasswordAuthenticationFilter.class); // අපේ Guard ව Filter එකක් විදිහට දානවා

        return http.build();
    }
}