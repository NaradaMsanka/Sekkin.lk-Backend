package com.cosmetic.ecommerce.service;

import com.cosmetic.ecommerce.entity.Product;
import com.cosmetic.ecommerce.repository.ProductRepository;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import java.util.HashSet;
import java.util.Set;
import java.util.List;

@Service
public class ProductImageService {
    private static final Logger log = LoggerFactory.getLogger(ProductImageService.class);
    private final ProductRepository products;
    private final CloudinaryService cloudinary;
    private final ObjectMapper mapper;

    public ProductImageService(ProductRepository products, CloudinaryService cloudinary, ObjectMapper mapper) {
        this.products = products;
        this.cloudinary = cloudinary;
        this.mapper = mapper;
    }

    public Set<String> imageUrls(Product product) {
        Set<String> urls = new HashSet<>();
        if (product.getImageUrl() != null && !product.getImageUrl().isBlank()) urls.add(product.getImageUrl());
        if (product.getImages() != null && !product.getImages().isBlank()) {
            try {
                List<String> gallery = mapper.readValue(product.getImages(), new TypeReference<List<String>>() {});
                if (gallery != null) gallery.stream().filter(url -> url != null && !url.isBlank()).forEach(urls::add);
            } catch (Exception ignored) {
                // Legacy comma-separated galleries. Never split a Base64 data URL.
                if (!product.getImages().startsWith("data:")) urls.addAll(List.of(product.getImages().split(",")));
            }
        }
        return urls;
    }

    // Never delete cloud assets until the product transaction commits successfully.
    public void cleanup(Set<String> previousUrls) {
        if (previousUrls.isEmpty()) return;
        Set<String> candidates = Set.copyOf(previousUrls);
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override public void afterCommit() { cleanupCommitted(candidates); }
            });
        } else {
            cleanupCommitted(candidates);
        }
    }

    private void cleanupCommitted(Set<String> previousUrls) {
        try {
            Set<String> referenced = new HashSet<>();
            products.findAll().forEach(product -> referenced.addAll(imageUrls(product)));
            previousUrls.stream().filter(url -> !referenced.contains(url)).forEach(cloudinary::deleteUnused);
        } catch (RuntimeException ex) {
            log.warn("Image cleanup deferred; product data was saved successfully.");
        }
    }
}
