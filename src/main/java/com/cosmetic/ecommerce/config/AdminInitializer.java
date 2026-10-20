package com.cosmetic.ecommerce.config;

import com.cosmetic.ecommerce.entity.User;
import com.cosmetic.ecommerce.repository.UserRepository;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.password.PasswordEncoder;

@Configuration
public class AdminInitializer {

    @Bean
    public CommandLineRunner initAdmin(UserRepository userRepository, PasswordEncoder passwordEncoder) {
        return args -> {
            userRepository.findByEmail("admin@sekkin.lk");
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
