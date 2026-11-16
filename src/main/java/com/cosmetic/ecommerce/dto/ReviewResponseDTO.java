package com.cosmetic.ecommerce.dto;

import lombok.Data;
import java.time.LocalDateTime;

@Data
public class ReviewResponseDTO {
    private Long id;
    private int rating;
    private String comment;
    private LocalDateTime createdAt;
    private String userEmail;
    private Long productId;
}