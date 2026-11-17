package com.cosmetic.ecommerce.dto;

import lombok.Data;
import lombok.ToString;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import com.cosmetic.ecommerce.validation.BcryptPasswordLength;

@Data
public class UserRegistrationDto {
    @NotBlank @Size(max = 160) private String name;
    @NotBlank @Email @Size(max = 254) private String email;
    @NotBlank @BcryptPasswordLength @ToString.Exclude private String password;
}
