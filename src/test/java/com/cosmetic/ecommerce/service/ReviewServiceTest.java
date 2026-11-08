package com.cosmetic.ecommerce.service;

import com.cosmetic.ecommerce.dto.ReviewRequestDTO;
import com.cosmetic.ecommerce.entity.Product;
import com.cosmetic.ecommerce.entity.Review;
import com.cosmetic.ecommerce.entity.User;
import com.cosmetic.ecommerce.repository.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.util.Optional;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ReviewServiceTest {
    @Mock ReviewRepository reviews;
    @Mock OrderRepository orders;
    @Mock ProductRepository products;
    @Mock UserRepository users;
    @InjectMocks ReviewService service;
    private static final String EMAIL = "buyer@example.test";

    private void existingAccountAndProduct() {
        User user = new User(); user.setId(7L); user.setEmail(EMAIL);
        Product product = new Product(); product.setId(2L);
        when(users.findByEmail(EMAIL)).thenReturn(user);
        when(products.findById(2L)).thenReturn(Optional.of(product));
    }

    private ReviewRequestDTO request(int rating, String comment) {
        ReviewRequestDTO request = new ReviewRequestDTO(); request.setRating(rating); request.setComment(comment); return request;
    }

    @Test void nonBuyerCannotWriteEvenWhenCallingServiceDirectly() {
        existingAccountAndProduct();
        var error = assertThrows(ResponseStatusException.class, () -> service.addReview(2L, request(5, "Review"), EMAIL));
        assertEquals(HttpStatus.FORBIDDEN, error.getStatusCode());
        verify(reviews, never()).save(any());
    }

    @Test void completedPurchaseCanWriteAndIsLinkedToAuthenticatedUser() {
        existingAccountAndProduct();
        when(orders.hasCompletedPurchase(7L, 2L)).thenReturn(true);
        when(reviews.save(any())).thenAnswer(call -> {
            Review review = call.getArgument(0); review.setId(10L); review.setCreatedAt(LocalDateTime.now()); return review;
        });
        var result = service.addReview(2L, request(4, "  Useful product  "), EMAIL);
        assertEquals(4, result.getRating()); assertEquals("Useful product", result.getComment());
        assertEquals(EMAIL, result.getUserEmail()); assertEquals(2L, result.getProductId());
    }

    @Test void duplicateReviewIsConflict() {
        existingAccountAndProduct();
        when(reviews.existsByUserIdAndProductId(7L, 2L)).thenReturn(true);
        var error = assertThrows(ResponseStatusException.class, () -> service.addReview(2L, request(5, "Again"), EMAIL));
        assertEquals(HttpStatus.CONFLICT, error.getStatusCode()); verify(reviews, never()).save(any());
    }

    @Test void eligibilityMatchesWritePermission() {
        existingAccountAndProduct(); when(orders.hasCompletedPurchase(7L, 2L)).thenReturn(true);
        assertTrue(service.getEligibility(2L, EMAIL).eligible());
        when(reviews.existsByUserIdAndProductId(7L, 2L)).thenReturn(true);
        assertFalse(service.getEligibility(2L, EMAIL).eligible());
        assertTrue(service.getEligibility(2L, EMAIL).alreadyReviewed());
    }

    @ParameterizedTest @ValueSource(ints = {0, 6, -1})
    void ratingMustBeBetweenOneAndFive(int rating) {
        existingAccountAndProduct(); when(orders.hasCompletedPurchase(7L, 2L)).thenReturn(true);
        var error = assertThrows(ResponseStatusException.class, () -> service.addReview(2L, request(rating, "Review"), EMAIL));
        assertEquals(HttpStatus.BAD_REQUEST, error.getStatusCode()); verify(reviews, never()).save(any());
    }

    @Test void blankOrOversizedCommentsAreRejected() {
        existingAccountAndProduct(); when(orders.hasCompletedPurchase(7L, 2L)).thenReturn(true);
        for (String comment : new String[]{"   ", "x".repeat(1001)}) {
            var error = assertThrows(ResponseStatusException.class, () -> service.addReview(2L, request(5, comment), EMAIL));
            assertEquals(HttpStatus.BAD_REQUEST, error.getStatusCode());
        }
        verify(reviews, never()).save(any());
    }

    @Test void unknownUserCannotClaimPurchase() {
        var error = assertThrows(ResponseStatusException.class, () -> service.getEligibility(2L, EMAIL));
        assertEquals(HttpStatus.UNAUTHORIZED, error.getStatusCode()); verifyNoInteractions(orders, reviews);
    }

    @Test void missingProductIsNotFound() {
        User user = new User(); when(users.findByEmail(EMAIL)).thenReturn(user);
        var error = assertThrows(ResponseStatusException.class, () -> service.getEligibility(99L, EMAIL));
        assertEquals(HttpStatus.NOT_FOUND, error.getStatusCode()); verifyNoInteractions(orders, reviews);
    }
}
