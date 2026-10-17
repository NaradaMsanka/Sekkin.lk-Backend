package com.cosmetic.ecommerce.service;

import com.cosmetic.ecommerce.dto.ReviewRequestDTO;
import com.cosmetic.ecommerce.dto.ReviewResponseDTO;
import com.cosmetic.ecommerce.entity.Order;
import com.cosmetic.ecommerce.entity.Product;
import com.cosmetic.ecommerce.entity.Review;
import com.cosmetic.ecommerce.entity.User;
import com.cosmetic.ecommerce.repository.OrderRepository;
import com.cosmetic.ecommerce.repository.ProductRepository;
import com.cosmetic.ecommerce.repository.ReviewRepository;
import com.cosmetic.ecommerce.repository.UserRepository;
import org.springframework.stereotype.Service;

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

    public ReviewResponseDTO addReview(Long productId, ReviewRequestDTO request, String userEmail) {
        User user = userRepository.findByEmail(userEmail);
        if (user == null) {
            throw new RuntimeException("User not found");
        }

        Product product = productRepository.findById(productId)
                .orElseThrow(() -> new RuntimeException("Product not found"));

        // එකම භාණ්ඩයට නැවත review එකක් දැමීම වැළැක්වීම
        if (reviewRepository.existsByUserIdAndProductId(user.getId(), product.getId())) {
            throw new RuntimeException("You have already reviewed this product.");
        }

        // Rule: මිලදී ගෙන ඇත්දැයි බැලීම (Order -> OrderItem -> Product)
        List<Order> orders = orderRepository.findByUser(user);
        boolean hasPurchased = orders.stream()
                .flatMap(order -> order.getOrderItems().stream())
                .anyMatch(item -> item.getProduct().getId().equals(productId));

        if (!hasPurchased) {
            throw new RuntimeException("Only customers who purchased this product can leave a review.");
        }

        Review review = new Review();
        review.setRating(request.getRating());
        review.setComment(request.getComment());
        review.setUser(user);
        review.setProduct(product);

        Review saved = reviewRepository.save(review);
        return mapToDTO(saved);
    }

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