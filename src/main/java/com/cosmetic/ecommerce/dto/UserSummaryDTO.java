package com.cosmetic.ecommerce.dto;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class UserSummaryDTO {
    private Long id;
    private String name;
    private String email;
    private String role;
}