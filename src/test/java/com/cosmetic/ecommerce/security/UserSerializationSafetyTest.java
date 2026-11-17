package com.cosmetic.ecommerce.security;

import com.cosmetic.ecommerce.entity.Cart;
import com.cosmetic.ecommerce.entity.User;
import com.cosmetic.ecommerce.controller.AuthController;
import com.cosmetic.ecommerce.dto.UserRegistrationDto;
import com.cosmetic.ecommerce.service.UserService;
import com.fasterxml.jackson.databind.json.JsonMapper;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class UserSerializationSafetyTest {
    private final JsonMapper json = new JsonMapper();

    private User fixture() {
        User user = new User();
        user.setName("Fixture customer");
        user.setEmail("fixture@example.test");
        user.setPassword("test-only-password-hash-must-stay-server-side");
        return user;
    }

    @Test
    void userResponseExcludesPasswordPropertyAndValue() throws Exception {
        String body = json.writeValueAsString(fixture());
        assertTrue(body.contains("fixture@example.test"));
        assertFalse(body.contains("password"));
        assertFalse(body.contains("test-only-password-hash"));
    }

    @Test
    void nestedCartResponseAlsoExcludesThePasswordHash() throws Exception {
        Cart cart = new Cart();
        cart.setUser(fixture());
        String body = json.writeValueAsString(cart);
        assertFalse(body.contains("\"user\""));
        assertFalse(body.contains("fixture@example.test"));
        assertFalse(body.contains("password"));
        assertFalse(body.contains("test-only-password-hash"));
    }

    @Test
    void generatedUserLogRepresentationExcludesThePasswordHash() {
        assertFalse(fixture().toString().contains("test-only-password-hash"));
    }

    @Test
    void registrationStillAcceptsPasswordThroughItsDedicatedRequestDto() throws Exception {
        UserService users = mock(UserService.class);
        when(users.registerUser(any(UserRegistrationDto.class))).thenReturn("User registered successfully!");
        AuthController controller = new AuthController();
        ReflectionTestUtils.setField(controller, "userService", users);
        var mvc = MockMvcBuilders.standaloneSetup(controller).build();
        mvc.perform(post("/api/auth/register").contentType("application/json")
                .content("{\"name\":\"Fixture\",\"email\":\"fixture@example.test\",\"password\":\"test-only-input-password\"}"))
                .andExpect(status().isOk());
        var request = ArgumentCaptor.forClass(UserRegistrationDto.class);
        verify(users).registerUser(request.capture());
        assertEquals("test-only-input-password", request.getValue().getPassword());
    }
}
