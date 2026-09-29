package com.cosmetic.ecommerce.controller;

import com.cosmetic.ecommerce.dto.UserRegistrationDto;
import com.cosmetic.ecommerce.service.UserService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import com.cosmetic.ecommerce.dto.UserLoginDto;
import com.cosmetic.ecommerce.dto.AuthResponseDto;
import com.cosmetic.ecommerce.util.JwtUtil;
import com.cosmetic.ecommerce.entity.User;
import com.cosmetic.ecommerce.repository.UserRepository;
import org.springframework.security.crypto.password.PasswordEncoder;
import java.util.Optional;

@RestController
@RequestMapping("/api/auth")
@CrossOrigin // connect to the reactApp
public class AuthController {

    @Autowired
    private UserService userService;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private JwtUtil jwtUtil;


    @PostMapping("/register")
    public ResponseEntity<String> register(@RequestBody UserRegistrationDto dto) {
        String response = userService.registerUser(dto);
        if (response.equals("Email is already registered!")) {
            return ResponseEntity.badRequest().body(response);
        }
        return ResponseEntity.ok(response);
    }

    //login API
    @PostMapping("/login")
    public ResponseEntity<?> login(@RequestBody UserLoginDto dto) {
        Optional<User> userOpt = Optional.ofNullable(userRepository.findByEmail(dto.getEmail()));

        if (userOpt.isPresent()) {
            User user = userOpt.get();
            // match the password
            if (passwordEncoder.matches(dto.getPassword(), user.getPassword())) {
                String token = jwtUtil.generateToken(user.getEmail());
                return ResponseEntity.ok(new AuthResponseDto(token));
            }
        }
        return ResponseEntity.status(401).body("Invalid email or password");
    }
}