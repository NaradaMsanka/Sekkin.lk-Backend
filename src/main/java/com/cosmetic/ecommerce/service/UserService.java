package com.cosmetic.ecommerce.service;

import com.cosmetic.ecommerce.dto.UserRegistrationDto;
import com.cosmetic.ecommerce.entity.User;
import com.cosmetic.ecommerce.repository.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Service
public class UserService {

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PasswordEncoder passwordEncoder; // SecurityConfig එකෙන් එන BCrypt Encoder එක

    public String registerUser(UserRegistrationDto dto) {
        if (userRepository.existsByEmail(dto.getEmail())) {
            return "Email is already registered!";
        }

        User user = new User();
        user.setName(dto.getName());
        user.setEmail(dto.getEmail());
        // Password එක Database එකට යන්න කලින් මෙතනදි Hash වෙනවා!
        user.setPassword(passwordEncoder.encode(dto.getPassword()));

        userRepository.save(user);
        return "User registered successfully!";
    }
}