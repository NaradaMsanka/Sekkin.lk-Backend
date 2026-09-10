package com.cosmetic.ecommerce.controller;

import com.cosmetic.ecommerce.entity.Product;
import com.cosmetic.ecommerce.service.ProductService;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/products")
public class ProductController {

    private final ProductService productService;

    public ProductController(ProductService productService) {
        this.productService = productService;
    }

    // POST Request
    @PostMapping
    public Product createProduct(@RequestBody Product product) {
        return productService.createProduct(product);
    }

    // GET Request
    @GetMapping
    public List<Product> getAllProducts() {
        return productService.getAllProducts();
    }
    // reqest product using ID
    @GetMapping("/{id}")
    public Product getProductById(@PathVariable Long id) {
        return productService.getProductById(id);
    }
}