package com.cosmetic.ecommerce.dto;

import com.cosmetic.ecommerce.validation.BcryptPasswordLength;
import jakarta.validation.constraints.*;
import lombok.Getter;
import lombok.Setter;

// No generated toString: credentials must never appear in log messages.
@Getter @Setter
public class ResetPasswordRequest {
    @NotBlank @Size(max = 128)
    private String token;
    @NotBlank @Size(min = 6, max = 72) @BcryptPasswordLength
    private String newPassword;
    @NotBlank @Size(max = 72)
    private String confirmPassword;
}
