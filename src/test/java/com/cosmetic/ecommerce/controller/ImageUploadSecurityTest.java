package com.cosmetic.ecommerce.controller;

import com.cosmetic.ecommerce.config.JwtFilter;
import com.cosmetic.ecommerce.config.SecurityConfig;
import com.cosmetic.ecommerce.service.CloudinaryService;
import com.cosmetic.ecommerce.service.ProductService;
import com.cosmetic.ecommerce.util.JwtUtil;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;
import java.util.Map;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest({FileUploadController.class, ProductController.class})
@Import({SecurityConfig.class, JwtFilter.class})
class ImageUploadSecurityTest {
    @Autowired private MockMvc mvc;
    @MockBean private CloudinaryService images;
    @MockBean private ProductService products;
    @MockBean private JwtUtil jwtUtil;

    private MockMultipartFile image() {
        return new MockMultipartFile("file", "product.webp", "image/webp", new byte[]{1, 2, 3});
    }

    @Test void anonymousCannotUpload() throws Exception {
        mvc.perform(multipart("/api/upload").file(image())).andExpect(status().isUnauthorized());
        verifyNoInteractions(images);
    }

    @Test @WithMockUser(authorities = "USER")
    void customerCannotUpload() throws Exception {
        mvc.perform(multipart("/api/upload").file(image())).andExpect(status().isForbidden());
        verifyNoInteractions(images);
    }

    @Test @WithMockUser(authorities = "ADMIN")
    void adminCanUpload() throws Exception {
        when(images.upload(any())).thenReturn(Map.of("url", "https://res.cloudinary.com/test/image/upload/v1/product.webp"));
        mvc.perform(multipart("/api/upload").file(image())).andExpect(status().isOk())
                .andExpect(jsonPath("$.url").value("https://res.cloudinary.com/test/image/upload/v1/product.webp"));
    }

    @Test @WithMockUser(authorities = "USER")
    void customerCannotRemoveProductImages() throws Exception {
        mvc.perform(put("/api/products/1").contentType("application/json")
                .content("{\"name\":\"Cream\",\"price\":100,\"stockQuantity\":1,\"imageUrl\":\"\",\"images\":\"[]\"}"))
                .andExpect(status().isForbidden());
        mvc.perform(delete("/api/products/1")).andExpect(status().isForbidden());
        verifyNoInteractions(products);
    }

    @Test @WithMockUser(authorities = "ADMIN")
    void adminCanDeleteProduct() throws Exception {
        mvc.perform(delete("/api/products/1")).andExpect(status().isOk());
        verify(products).deleteProduct(1L);
    }
}
