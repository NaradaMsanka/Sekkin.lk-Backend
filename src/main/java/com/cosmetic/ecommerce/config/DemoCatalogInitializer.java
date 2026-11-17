package com.cosmetic.ecommerce.config;

import com.cosmetic.ecommerce.entity.Product;
import com.cosmetic.ecommerce.repository.ProductRepository;
import java.math.BigDecimal;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

/** Synthetic examples for an explicitly selected, temporary local demonstration. */
@Configuration
@Profile("demo & !production & !prod")
public class DemoCatalogInitializer {
    @Bean
    CommandLineRunner demoCatalog(ProductRepository products) {
        return args -> {
            if (products.count() != 0) return;
            products.save(product("Demo Gentle Cleanser", "1000.00", "All skin types", 10));
            products.save(product("Demo Daily Moisturiser", "2500.00", "Dry", 5));
            products.save(product("Demo Hydrating Serum", "3000.00", "Combination", 3));
        };
    }

    private Product product(String name, String price, String skinType, int stock) {
        Product product = new Product();
        product.setName(name);
        product.setBrand("SEKKIN Demo");
        product.setDescription("Synthetic product for the local project demonstration. Not a live sale listing.");
        product.setPrice(new BigDecimal(price));
        product.setStockQuantity(stock);
        product.setSkinType(skinType);
        product.setStatus("ACTIVE");
        return product;
    }
}
