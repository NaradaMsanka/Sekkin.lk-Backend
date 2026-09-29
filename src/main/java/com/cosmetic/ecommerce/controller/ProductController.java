package com.cosmetic.ecommerce.controller;

import com.cosmetic.ecommerce.dto.ProductDTO;
import com.cosmetic.ecommerce.entity.Product;
import com.cosmetic.ecommerce.service.ProductService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/products")
@CrossOrigin
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
    @PutMapping("/{id}")
    public Product updateProduct(@PathVariable Long id, @Valid @RequestBody ProductDTO dto) {
        Product product = new Product();
        product.setName(dto.getName());
        product.setDescription(dto.getDescription());
        product.setPrice(dto.getPrice());
        product.setDiscountPrice(dto.getDiscountPrice());
        product.setBrand(dto.getBrand());
        product.setStockQuantity(dto.getStockQuantity());
        product.setSkinType(dto.getSkinType());
        product.setIngredients(dto.getIngredients());
        product.setStatus(dto.getStatus());

        return productService.updateProduct(id, product);
    }

    @DeleteMapping("/{id}")
    public String deleteProduct(@PathVariable Long id) {
        productService.deleteProduct(id);
        return "Product deleted successfully!";
    }
}