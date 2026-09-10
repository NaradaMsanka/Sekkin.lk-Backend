package com.cosmetic.ecommerce.service;

import com.cosmetic.ecommerce.entity.Product;
import com.cosmetic.ecommerce.repository.ProductRepository;
import org.springframework.stereotype.Service;
import java.util.List;

@Service
public class ProductService {

    private final ProductRepository productRepository;

    public ProductService(ProductRepository productRepository) {
        this.productRepository = productRepository;
    }

    // save new product
    public Product createProduct(Product product) {
        return productRepository.save(product);
    }

    // get all data in the database
    public List<Product> getAllProducts() {
        return productRepository.findAll();
    }
    //use id to search product
    public Product getProductById(Long id) {
        return productRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Product not found with id: " + id));
    }
}