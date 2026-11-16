package com.cosmetic.ecommerce.config;

import com.cosmetic.ecommerce.entity.User;
import com.cosmetic.ecommerce.repository.UserRepository;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.jdbc.core.JdbcTemplate;

@Configuration
public class AdminInitializer {

    @Bean
    public CommandLineRunner initAdmin(UserRepository userRepository, PasswordEncoder passwordEncoder, JdbcTemplate jdbcTemplate) {
        return args -> {
            try {
                jdbcTemplate.execute("ALTER TABLE orders ALTER COLUMN user_id DROP NOT NULL;");
            } catch (Exception e) {
                System.out.println("Notice: Could not alter orders.user_id: " + e.getMessage());
            }

            try {
                jdbcTemplate.execute("UPDATE users SET role = 'USER' WHERE role IS NULL OR role = '';");
            } catch (Exception e) {
                System.out.println("Notice: Could not patch users role: " + e.getMessage());
            }

            if (!userRepository.existsByEmail("admin@sekkin.lk")) {
                User admin = new User();
                admin.setName("Sekkin Admin");
                admin.setEmail("admin@sekkin.lk");
                admin.setPassword(passwordEncoder.encode("sekkin.lk@2026"));
                admin.setRole("ADMIN");
                userRepository.save(admin);
            }
        };
    }
}
