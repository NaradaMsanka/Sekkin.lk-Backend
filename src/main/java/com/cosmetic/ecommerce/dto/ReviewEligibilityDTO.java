package com.cosmetic.ecommerce.dto;

public record ReviewEligibilityDTO(boolean eligible, boolean hasPurchased,
                                   boolean alreadyReviewed, String message) {
}
