package com.cosmetic.ecommerce.controller;

import com.cosmetic.ecommerce.dto.*;
import com.cosmetic.ecommerce.service.*;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import java.util.Map;

@RestController
@RequestMapping("/api/auth")
public class PasswordResetController {
    public static final String CONFIRMATION = "If an account with that email exists, you will receive a password reset link shortly.";
    private static final Logger LOG = LoggerFactory.getLogger(PasswordResetController.class);
    private final PasswordResetDispatcher dispatcher;
    private final PasswordResetService resets;
    public PasswordResetController(PasswordResetDispatcher dispatcher, PasswordResetService resets) { this.dispatcher = dispatcher; this.resets = resets; }

    @PostMapping("/forgot-password")
    public ResponseEntity<Map<String, String>> forgot(@Valid @RequestBody ForgotPasswordRequest request) {
        dispatcher.request(request.getEmail());
        return reply(HttpStatus.OK, CONFIRMATION);
    }

    @PostMapping("/reset-password")
    public ResponseEntity<Map<String, String>> reset(@Valid @RequestBody ResetPasswordRequest request) {
        resets.reset(request.getToken(), request.getNewPassword(), request.getConfirmPassword());
        return reply(HttpStatus.OK, "Your password has been reset. Sign in with your new password.");
    }

    @ExceptionHandler(ResponseStatusException.class)
    ResponseEntity<Map<String, String>> rejected(ResponseStatusException error) {
        return reply(error.getStatusCode(), error.getReason());
    }

    @ExceptionHandler(Exception.class)
    ResponseEntity<Map<String, String>> unavailable(Exception ignored) {
        // Validation is handled by RequestValidationAdvice. Keep framework errors generic here too.
        if (ignored instanceof org.springframework.web.bind.MethodArgumentNotValidException
                || ignored instanceof org.springframework.http.converter.HttpMessageNotReadableException)
            return reply(HttpStatus.BAD_REQUEST, "Invalid request. Check the required fields.");
        LOG.warn("Password recovery operation did not complete.");
        return reply(HttpStatus.SERVICE_UNAVAILABLE, "Password recovery is temporarily unavailable. Please try again later.");
    }

    private static ResponseEntity<Map<String, String>> reply(HttpStatusCode status, String message) {
        return ResponseEntity.status(status).cacheControl(CacheControl.noStore()).header("Referrer-Policy", "no-referrer")
                .body(Map.of("message", message));
    }
}
