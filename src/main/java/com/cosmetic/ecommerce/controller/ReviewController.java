package com.cosmetic.ecommerce.controller;

import com.cosmetic.ecommerce.dto.ReviewRequestDTO;
import com.cosmetic.ecommerce.dto.ReviewResponseDTO;
import com.cosmetic.ecommerce.service.ReviewService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/products")
public class ReviewController {

    private final ReviewService reviewService;

    public ReviewController(ReviewService reviewService) {
        this.reviewService = reviewService;
    }

    @PostMapping("/{id}/reviews")
    public ResponseEntity<ReviewResponseDTO> addReview(@PathVariable Long id,
                                                       @RequestBody ReviewRequestDTO request,
                                                       Authentication authentication) {
        String userEmail = authentication.getName();
        ReviewResponseDTO response = reviewService.addReview(id, request, userEmail);
        return new ResponseEntity<>(response, HttpStatus.CREATED);
    }

    @GetMapping("/{id}/reviews")
    public ResponseEntity<List<ReviewResponseDTO>> getReviews(@PathVariable Long id) {
        return ResponseEntity.ok(reviewService.getReviewsByProduct(id));
    }
}