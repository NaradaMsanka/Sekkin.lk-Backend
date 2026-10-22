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
    //u[date and delete product
    public Product updateProduct(Long id, Product updatedProduct) {
        Product existing = getProductById(id);
        existing.setName(updatedProduct.getName());
        existing.setDescription(updatedProduct.getDescription());
        existing.setPrice(updatedProduct.getPrice());
        existing.setDiscountPrice(updatedProduct.getDiscountPrice());
        existing.setBrand(updatedProduct.getBrand());
        existing.setStockQuantity(updatedProduct.getStockQuantity());
        existing.setSkinType(updatedProduct.getSkinType());
        existing.setIngredients(updatedProduct.getIngredients());
        existing.setStatus(updatedProduct.getStatus());
        if (updatedProduct.getImageUrl() != null) {
            existing.setImageUrl(updatedProduct.getImageUrl());
        }
        if (updatedProduct.getImages() != null) {
            existing.setImages(updatedProduct.getImages());
        }
        return productRepository.save(existing);
    }

    public void deleteProduct(Long id) {
        Product product = getProductById(id);
        productRepository.delete(product);
    }

    public List<Product> filterProducts(String brand, String skinType, Double minPrice, Double maxPrice) {
        return productRepository.searchAndFilterProducts(brand, skinType, minPrice, maxPrice);
    }
}