package com.cosmetic.ecommerce.service;

import com.cosmetic.ecommerce.dto.ReviewRequestDTO;
import com.cosmetic.ecommerce.dto.ReviewResponseDTO;
import com.cosmetic.ecommerce.dto.ReviewEligibilityDTO;
import com.cosmetic.ecommerce.entity.Product;
import com.cosmetic.ecommerce.entity.Review;
import com.cosmetic.ecommerce.entity.User;
import com.cosmetic.ecommerce.repository.OrderRepository;
import com.cosmetic.ecommerce.repository.ProductRepository;
import com.cosmetic.ecommerce.repository.ReviewRepository;
import com.cosmetic.ecommerce.repository.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.http.HttpStatus;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.stream.Collectors;

@Service
public class ReviewService {

    private final ReviewRepository reviewRepository;
    private final OrderRepository orderRepository;
    private final ProductRepository productRepository;
    private final UserRepository userRepository;

    public ReviewService(ReviewRepository reviewRepository,
                         OrderRepository orderRepository,
                         ProductRepository productRepository,
                         UserRepository userRepository) {
        this.reviewRepository = reviewRepository;
        this.orderRepository = orderRepository;
        this.productRepository = productRepository;
        this.userRepository = userRepository;
    }

    @Transactional(readOnly = true)
    public ReviewEligibilityDTO getEligibility(Long productId, String userEmail) {
        return eligibility(requireUser(userEmail), requireProduct(productId));
    }

    @Transactional
    public ReviewResponseDTO addReview(Long productId, ReviewRequestDTO request, String userEmail) {
        User user = requireUser(userEmail);
        Product product = requireProduct(productId);
        ReviewEligibilityDTO eligibility = eligibility(user, product);
        if (eligibility.alreadyReviewed()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, eligibility.message());
        }
        if (!eligibility.hasPurchased()) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, eligibility.message());
        }
        if (request == null || request.getRating() < 1 || request.getRating() > 5) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Choose a rating from 1 to 5.");
        }
        if (request.getComment() == null || request.getComment().isBlank() || request.getComment().length() > 1000) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Write a review of 1 to 1000 characters.");
        }

        Review review = new Review();
        review.setRating(request.getRating());
        review.setComment(request.getComment().trim());
        review.setUser(user);
        review.setProduct(product);

        Review saved = reviewRepository.save(review);
        return mapToDTO(saved);
    }

    private User requireUser(String email) {
        User user = email == null ? null : userRepository.findByEmail(email);
        if (user == null) throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Sign in to review a purchase.");
        return user;
    }

    private Product requireProduct(Long productId) {
        return productRepository.findById(productId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Product not found."));
    }

    private ReviewEligibilityDTO eligibility(User user, Product product) {
        boolean reviewed = reviewRepository.existsByUserIdAndProductId(user.getId(), product.getId());
        boolean purchased = orderRepository.hasCompletedPurchase(user.getId(), product.getId());
        String message = reviewed ? "You have already reviewed this product."
                : purchased ? "Your purchase is verified. Share your experience."
                : "Reviews are available after an order containing this product is delivered or completed.";
        return new ReviewEligibilityDTO(purchased && !reviewed, purchased, reviewed, message);
    }

    @Transactional(readOnly = true)
    public List<ReviewResponseDTO> getReviewsByProduct(Long productId) {
        return reviewRepository.findByProductId(productId).stream()
                .map(this::mapToDTO)
                .collect(Collectors.toList());
    }

    private ReviewResponseDTO mapToDTO(Review review) {
        ReviewResponseDTO dto = new ReviewResponseDTO();
        dto.setId(review.getId());
        dto.setRating(review.getRating());
        dto.setComment(review.getComment());
        dto.setCreatedAt(review.getCreatedAt());
        dto.setUserEmail(review.getUser().getEmail());
        dto.setProductId(review.getProduct().getId());
        return dto;
    }
}
