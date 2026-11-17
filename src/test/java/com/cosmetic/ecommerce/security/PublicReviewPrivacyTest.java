package com.cosmetic.ecommerce.security;

import com.cosmetic.ecommerce.entity.*;
import com.cosmetic.ecommerce.repository.*;
import com.cosmetic.ecommerce.service.ReviewService;
import com.fasterxml.jackson.databind.json.JsonMapper;
import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class PublicReviewPrivacyTest {
    @Test void publicReviewsContainReviewDataWithoutAccountEmail() throws Exception {
        ReviewRepository reviews = mock(ReviewRepository.class);
        ReviewService service = new ReviewService(reviews, mock(OrderRepository.class), mock(ProductRepository.class), mock(UserRepository.class));
        User user = new User(); user.setEmail("private-reviewer@example.test"); user.setName("Private name");
        Product product = new Product(); product.setId(2L);
        Review review = new Review(); review.setId(9L); review.setUser(user); review.setProduct(product);
        review.setRating(4); review.setComment("Useful product");
        when(reviews.findByProductId(2L)).thenReturn(List.of(review));
        String json = JsonMapper.builder().findAndAddModules().build().writeValueAsString(service.getReviewsByProduct(2L));
        assertTrue(json.contains("Useful product")); assertTrue(json.contains("\"rating\":4"));
        assertFalse(json.contains("userEmail")); assertFalse(json.contains("private-reviewer")); assertFalse(json.contains("Private name"));
    }
}
