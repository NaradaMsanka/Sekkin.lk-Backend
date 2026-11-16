package com.cosmetic.ecommerce.controller;

import com.cosmetic.ecommerce.config.JwtFilter;
import com.cosmetic.ecommerce.config.SecurityConfig;
import com.cosmetic.ecommerce.dto.ReviewEligibilityDTO;
import com.cosmetic.ecommerce.dto.ReviewResponseDTO;
import com.cosmetic.ecommerce.service.ReviewService;
import com.cosmetic.ecommerce.util.JwtUtil;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.server.ResponseStatusException;
import java.util.List;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(ReviewController.class)
@Import({SecurityConfig.class, JwtFilter.class, ReviewExceptionHandler.class})
class ReviewSecurityTest {
    @Autowired MockMvc mvc;
    @MockBean ReviewService reviews;
    @MockBean JwtUtil jwt;
    private static final String BODY = "{\"rating\":5,\"comment\":\"Useful product\"}";

    @Test void anonymousCanReadButCannotWriteOrCheckAccountEligibility() throws Exception {
        when(reviews.getReviewsByProduct(2L)).thenReturn(List.of());
        mvc.perform(get("/api/products/2/reviews")).andExpect(status().isOk());
        mvc.perform(post("/api/products/2/reviews").contentType("application/json").content(BODY)).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/products/2/reviews/eligibility")).andExpect(status().isUnauthorized());
        verify(reviews, never()).addReview(anyLong(), any(), anyString());
        verify(reviews, never()).getEligibility(anyLong(), anyString());
    }

    @Test @WithMockUser(username = "buyer@example.test", authorities = "USER")
    void authenticatedBuyerCanSubmitAndCheckEligibility() throws Exception {
        ReviewResponseDTO response = new ReviewResponseDTO(); response.setId(9L); response.setRating(5);
        when(reviews.addReview(eq(2L), any(), eq("buyer@example.test"))).thenReturn(response);
        when(reviews.getEligibility(2L, "buyer@example.test")).thenReturn(new ReviewEligibilityDTO(true, true, false, "Verified"));
        mvc.perform(post("/api/products/2/reviews").contentType("application/json").content(BODY)).andExpect(status().isCreated()).andExpect(jsonPath("$.id").value(9));
        mvc.perform(get("/api/products/2/reviews/eligibility")).andExpect(status().isOk()).andExpect(jsonPath("$.eligible").value(true));
    }

    @Test @WithMockUser(username = "customer@example.test", authorities = "USER")
    void nonBuyerIsForbiddenWithReadableMessage() throws Exception {
        when(reviews.addReview(eq(2L), any(), eq("customer@example.test")))
                .thenThrow(new ResponseStatusException(HttpStatus.FORBIDDEN, "Purchase required."));
        mvc.perform(post("/api/products/2/reviews").contentType("application/json").content(BODY))
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.message").value("Purchase required."));
    }

    @Test @WithMockUser(username = "buyer@example.test", authorities = "USER")
    void invalidReviewNeverReachesService() throws Exception {
        for (String body : List.of("{\"rating\":0,\"comment\":\"Text\"}", "{\"rating\":6,\"comment\":\"Text\"}", "{\"rating\":5,\"comment\":\"   \"}")) {
            mvc.perform(post("/api/products/2/reviews").contentType("application/json").content(body))
                    .andExpect(status().isBadRequest()).andExpect(jsonPath("$.message").isString());
        }
        verifyNoInteractions(reviews);
    }
}
