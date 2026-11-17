package com.cosmetic.ecommerce.config;

import com.cosmetic.ecommerce.entity.User;
import com.cosmetic.ecommerce.repository.UserRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.security.crypto.password.PasswordEncoder;

/** Explicit local account setup only; this initializer performs no schema/data-repair SQL. */
@Configuration
@Profile("local-admin-bootstrap & !production & !prod")
public class AdminInitializer {
    @Bean
    public CommandLineRunner initAdmin(UserRepository users, PasswordEncoder encoder,
            @Value("${BOOTSTRAP_ADMIN_EMAIL:}") String email,
            @Value("${BOOTSTRAP_ADMIN_PASSWORD:}") String password) {
        if (email == null || email.isBlank() || !email.contains("@") || password == null || password.length() < 16) {
            throw new IllegalArgumentException("Explicit local admin email and a password of at least 16 characters are required.");
        }
        return args -> {
            if (users.existsByEmail(email)) return;
            User admin = new User();
            admin.setName("Local administrator");
            admin.setEmail(email);
            admin.setPassword(encoder.encode(password));
            admin.setRole("ADMIN");
            users.save(admin);
        };
    }
}
