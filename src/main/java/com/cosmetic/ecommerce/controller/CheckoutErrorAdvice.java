package com.cosmetic.ecommerce.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import java.util.Map;

@RestControllerAdvice(assignableTypes = {PromotionController.class, CheckoutQuoteController.class, OrderController.class})
public class CheckoutErrorAdvice {
    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<Map<String, String>> checkoutError(ResponseStatusException error) {
        return ResponseEntity.status(error.getStatusCode()).headers(error.getHeaders())
                .body(Map.of("message", error.getReason() == null ? "This request could not be completed." : error.getReason()));
    }
}
