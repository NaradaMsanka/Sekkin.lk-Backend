package com.cosmetic.ecommerce.dto;

import lombok.Data;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

@Data
public class ReviewRequestDTO {
    @Min(value = 1, message = "Choose a rating from 1 to 5.")
    @Max(value = 5, message = "Choose a rating from 1 to 5.")
    private int rating;
    @NotBlank(message = "Write a review before submitting.")
    @Size(max = 1000, message = "Reviews must be 1000 characters or fewer.")
    private String comment;
}
