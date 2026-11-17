package com.cosmetic.ecommerce.controller;

import com.cosmetic.ecommerce.dto.UserRegistrationDto;
import com.cosmetic.ecommerce.service.UserService;
import com.cosmetic.ecommerce.service.AccountSecurityService;
import com.cosmetic.ecommerce.security.RequestThrottle;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import com.cosmetic.ecommerce.dto.UserLoginDto;
import com.cosmetic.ecommerce.dto.AuthResponseDto;
import com.cosmetic.ecommerce.util.JwtUtil;
import com.cosmetic.ecommerce.entity.User;
import com.cosmetic.ecommerce.repository.UserRepository;
import org.springframework.security.crypto.password.PasswordEncoder;
import jakarta.validation.Valid;
import java.util.Optional;
import java.security.Principal;
import java.time.Duration;
import java.util.Locale;
import jakarta.servlet.http.HttpServletRequest;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    @Autowired
    private UserService userService;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private JwtUtil jwtUtil;

    @Autowired
    private AccountSecurityService accountSecurity;

    @Autowired
    private RequestThrottle throttle;

    @PostMapping("/register")
    public ResponseEntity<String> register(@Valid @RequestBody UserRegistrationDto dto) {
        String response = userService.registerUser(dto);
        if (response.equals("Email is already registered!")) {
            return ResponseEntity.badRequest().body(response);
        }
        return ResponseEntity.ok(response);
    }

    // login API
    @PostMapping("/login")
    public ResponseEntity<?> login(@Valid @RequestBody UserLoginDto dto, HttpServletRequest request) {
        long retry = throttle.attempt("login-identity-at-address", request.getRemoteAddr() + "\n"
                + dto.getEmail().trim().toLowerCase(Locale.ROOT), 10, Duration.ofMinutes(5));
        if (retry > 0) return ResponseEntity.status(429).header("Retry-After", Long.toString(retry))
                .body("Too many requests; try again later");
        Optional<User> userOpt = Optional.ofNullable(userRepository.findByEmail(dto.getEmail()));

        if (userOpt.isPresent()) {
            User user = userOpt.get();
            if (user.isEnabled() && passwordEncoder.matches(dto.getPassword(), user.getPassword())) {
                String role = user.getRole();
                if (!"USER".equals(role) && !"ADMIN".equals(role)) {
                    return ResponseEntity.status(401).body("Invalid email or password");
                }
                String token = jwtUtil.generateToken(user.getEmail(), role, user.getSessionVersion());
                return ResponseEntity.ok(new AuthResponseDto(token));
            }
        }
        return ResponseEntity.status(401).body("Invalid email or password");
    }

    @PostMapping("/logout-all")
    public ResponseEntity<Void> logoutAll(Principal principal) {
        try {
            accountSecurity.revokeCurrentSessions(principal.getName());
            return ResponseEntity.noContent().build();
        } catch (IllegalArgumentException unavailable) {
            // A concurrent disable/delete can invalidate the account after the authentication filter.
            return ResponseEntity.status(409).build();
        }
    }
}
