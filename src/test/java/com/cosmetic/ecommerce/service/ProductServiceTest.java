package com.cosmetic.ecommerce.service;

import com.cosmetic.ecommerce.entity.Product;
import com.cosmetic.ecommerce.repository.ProductRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import java.math.BigDecimal;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ProductServiceTest {

    @Mock
    private ProductRepository productRepository; // create sample database not exact one

    @InjectMocks
    private ProductService productService; // testing service

    @Test
    void testCreateProduct() {
        // 1. Arrange
        Product product = new Product();
        product.setName("Test Cream");
        product.setPrice(new BigDecimal("1500.00"));

        when(productRepository.save(any(Product.class))).thenReturn(product);

        // 2. Act
        Product savedProduct = productService.createProduct(product);

        // 3. Assert
        assertNotNull(savedProduct);
        assertEquals("Test Cream", savedProduct.getName());
        verify(productRepository, times(1)).save(any(Product.class));
    }

    @Test
    void testGetAllProducts() {
        when(productRepository.findAll()).thenReturn(List.of(new Product(), new Product()));
        List<Product> products = productService.getAllProducts();
        assertEquals(2, products.size());
    }
}