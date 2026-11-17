package com.cosmetic.ecommerce.validation;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;
import java.lang.annotation.*;

/** Bounds complete UTF-8 input before BCrypt; the algorithm must never silently compare a prefix. */
@Documented
@Constraint(validatedBy = BcryptPasswordLengthValidator.class)
@Target({ElementType.FIELD, ElementType.PARAMETER})
@Retention(RetentionPolicy.RUNTIME)
public @interface BcryptPasswordLength {
    String message() default "Password must contain at most 72 UTF-8 bytes";
    Class<?>[] groups() default {};
    Class<? extends Payload>[] payload() default {};
}
