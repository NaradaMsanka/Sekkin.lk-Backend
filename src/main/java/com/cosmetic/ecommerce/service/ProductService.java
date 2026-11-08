package com.cosmetic.ecommerce.service;

import com.cosmetic.ecommerce.entity.Product;
import com.cosmetic.ecommerce.repository.ProductRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.List;
import java.util.Set;

@Service
public class ProductService {

    private final ProductRepository productRepository;
    private final ProductImageService productImageService;

    public ProductService(ProductRepository productRepository, ProductImageService productImageService) {
        this.productRepository = productRepository;
        this.productImageService = productImageService;
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
    @Transactional
    public Product updateProduct(Long id, Product updatedProduct) {
        Product existing = getProductById(id);
        Set<String> previousImages = productImageService.imageUrls(existing);
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
        Product saved = productRepository.save(existing);
        previousImages.removeAll(productImageService.imageUrls(saved));
        productImageService.cleanup(previousImages);
        return saved;
    }

    @Transactional
    public void deleteProduct(Long id) {
        Product product = getProductById(id);
        Set<String> previousImages = productImageService.imageUrls(product);
        productRepository.delete(product);
        productImageService.cleanup(previousImages);
    }

    public List<Product> filterProducts(String brand, String skinType, Double minPrice, Double maxPrice) {
        return productRepository.searchAndFilterProducts(brand, skinType, minPrice, maxPrice);
    }
}
