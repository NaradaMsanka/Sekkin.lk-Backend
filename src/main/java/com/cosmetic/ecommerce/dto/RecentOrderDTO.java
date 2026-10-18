package com.cosmetic.ecommerce.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import java.time.LocalDateTime;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class RecentOrderDTO {
    private Long id;
    private String customerEmail;
    private Double totalAmount;
    private String status;
    private LocalDateTime orderDate;
}