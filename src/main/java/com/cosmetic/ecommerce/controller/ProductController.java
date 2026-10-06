package com.cosmetic.ecommerce.controller;

import com.cosmetic.ecommerce.dto.ProductDTO;
import com.cosmetic.ecommerce.entity.Product;
import com.cosmetic.ecommerce.service.ProductService;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
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

    // POST Request - (ADMIN ට විතරක් සීමා කර ඇත)
    @PreAuthorize("hasAuthority('ADMIN')")
    @PostMapping
    public Product createProduct(@RequestBody Product product) {
        return productService.createProduct(product);
    }

    // GET Request - (Products ඔක්කොම බලන්න සහ Filter කරන්න)
    @GetMapping
    public List<Product> getAllProducts(
            @RequestParam(required = false) String brand,
            @RequestParam(required = false) String skinType,
            @RequestParam(required = false) Double minPrice,
            @RequestParam(required = false) Double maxPrice) {

        // ෆිල්ටර් මුකුත් එවලා නැත්නම්, ඔක්කොම Products යවනවා
        if (brand == null && skinType == null && minPrice == null && maxPrice == null) {
            return productService.getAllProducts();
        }

        // ෆිල්ටර් එවලා තියෙනවා නම්, ඒවට ගැලපෙන ටික විතරක් යවනවා
        return productService.filterProducts(brand, skinType, minPrice, maxPrice);
    }

    // request product using ID - (හැමෝටම Products බලන්න පුළුවන්)
    @GetMapping("/{id}")
    public Product getProductById(@PathVariable Long id) {
        return productService.getProductById(id);
    }

    // PUT Request - (ADMIN ට විතරක් සීමා කර ඇත)
    @PreAuthorize("hasAuthority('ADMIN')")
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

    // DELETE Request - (ADMIN ට විතරක් සීමා කර ඇත)
    @PreAuthorize("hasAuthority('ADMIN')")
    @DeleteMapping("/{id}")
    public String deleteProduct(@PathVariable Long id) {
        productService.deleteProduct(id);
        return "Product deleted successfully!";
    }
}