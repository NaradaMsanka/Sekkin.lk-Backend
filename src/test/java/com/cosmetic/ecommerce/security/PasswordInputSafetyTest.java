package com.cosmetic.ecommerce.security;

import com.cosmetic.ecommerce.dto.UserLoginDto;
import com.cosmetic.ecommerce.dto.UserRegistrationDto;
import jakarta.validation.Validation;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import static org.junit.jupiter.api.Assertions.*;

class PasswordInputSafetyTest {
    private UserLoginDto login(String password) {
        UserLoginDto dto = new UserLoginDto(); dto.setEmail("fixture@example.test"); dto.setPassword(password); return dto;
    }
    private UserRegistrationDto registration(String password) {
        UserRegistrationDto dto = new UserRegistrationDto(); dto.setName("Synthetic fixture");
        dto.setEmail("fixture@example.test"); dto.setPassword(password); return dto;
    }

    @Test void checksTheEntirePasswordAtTheAsciiByteBoundaryForBothRequests() {
        try (ValidatorFactory factory = Validation.buildDefaultValidatorFactory()) {
            for (Object dto : new Object[]{login("a".repeat(72)), registration("a".repeat(72))})
                assertTrue(factory.getValidator().validate(dto).isEmpty());
            for (Object dto : new Object[]{login("a".repeat(72) + "b"), registration("a".repeat(72) + "b")})
                assertFalse(factory.getValidator().validate(dto).isEmpty());
        }
    }

    @Test void countsUtf8BytesRatherThanJavaCharacters() {
        try (ValidatorFactory factory = Validation.buildDefaultValidatorFactory()) {
            for (String password : new String[]{"é".repeat(36), "😀".repeat(18)}) {
                assertTrue(factory.getValidator().validate(login(password)).isEmpty());
                assertTrue(factory.getValidator().validate(registration(password)).isEmpty());
            }
            for (String password : new String[]{"é".repeat(37), "😀".repeat(19)}) {
                assertFalse(factory.getValidator().validate(login(password)).isEmpty());
                assertFalse(factory.getValidator().validate(registration(password)).isEmpty());
            }
        }
    }

    @Test void rejectsMalformedUnicodeAndStillRequiresANonblankPassword() {
        try (ValidatorFactory factory = Validation.buildDefaultValidatorFactory()) {
            for (String password : new String[]{null, "", " ", "\uD800", "\uDC00"}) {
                assertFalse(factory.getValidator().validate(login(password)).isEmpty());
                assertFalse(factory.getValidator().validate(registration(password)).isEmpty());
            }
        }
    }

    @Test void preservesValidExistingBcryptHashesAndDoesNotTransformInput() {
        BCryptPasswordEncoder bcrypt = new BCryptPasswordEncoder();
        String input = "synthetic-valid-existing-password";
        String existingHash = bcrypt.encode(input);
        try (ValidatorFactory factory = Validation.buildDefaultValidatorFactory()) {
            UserLoginDto request = login(input);
            assertTrue(factory.getValidator().validate(request).isEmpty());
            assertEquals(input, request.getPassword());
            assertTrue(bcrypt.matches(request.getPassword(), existingHash));
            assertFalse(bcrypt.matches("synthetic-different-password", existingHash));
        }
    }

    @Test void boundsContactFieldSizesWithoutInventingAMinimumPasswordPolicy() {
        try (ValidatorFactory factory = Validation.buildDefaultValidatorFactory()) {
            assertTrue(factory.getValidator().validate(registration("x")).isEmpty());
            UserRegistrationDto registration = registration("synthetic-password"); registration.setName("x".repeat(161));
            assertFalse(factory.getValidator().validate(registration).isEmpty());
            UserLoginDto login = login("synthetic-password"); login.setEmail("a".repeat(245) + "@example.test");
            assertFalse(factory.getValidator().validate(login).isEmpty());
        }
    }
}
