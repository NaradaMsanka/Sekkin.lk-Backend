package com.cosmetic.ecommerce.controller;

import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import java.util.Map;

/** Do not expose or log rejected request/credential values through framework validation errors. */
@RestControllerAdvice
@Order(Ordered.LOWEST_PRECEDENCE)
public class RequestValidationAdvice {
    @ExceptionHandler({MethodArgumentNotValidException.class, HttpMessageNotReadableException.class})
    public ResponseEntity<Map<String, String>> invalidRequest(Exception ignored) {
        return ResponseEntity.badRequest().body(Map.of("message", "Invalid request. Check the required fields."));
    }
}
