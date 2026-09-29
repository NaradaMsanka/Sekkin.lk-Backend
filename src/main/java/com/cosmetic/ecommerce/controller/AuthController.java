package com.cosmetic.ecommerce.controller;

import com.cosmetic.ecommerce.dto.UserRegistrationDto;
import com.cosmetic.ecommerce.service.UserService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/auth")
@CrossOrigin // React එකට සම්බන්ධ වෙන්න
public class AuthController {

    @Autowired
    private UserService userService;

    @PostMapping("/register")
    public ResponseEntity<String> register(@RequestBody UserRegistrationDto dto) {
        String response = userService.registerUser(dto);
        if (response.equals("Email is already registered!")) {
            return ResponseEntity.badRequest().body(response);
        }
        return ResponseEntity.ok(response);
    }
}