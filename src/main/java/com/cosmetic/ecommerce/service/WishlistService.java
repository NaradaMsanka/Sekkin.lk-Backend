package com.cosmetic.ecommerce.service;

import com.cosmetic.ecommerce.dto.WishlistItemResponseDTO;
import com.cosmetic.ecommerce.entity.Product;
import com.cosmetic.ecommerce.entity.User;
import com.cosmetic.ecommerce.entity.WishlistItem;
import com.cosmetic.ecommerce.repository.ProductRepository;
import com.cosmetic.ecommerce.repository.UserRepository;
import com.cosmetic.ecommerce.repository.WishlistRepository;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

@Service
public class WishlistService {

    private final WishlistRepository wishlistRepository;
    private final UserRepository userRepository;
    private final ProductRepository productRepository;

    public WishlistService(WishlistRepository wishlistRepository,
                           UserRepository userRepository,
                           ProductRepository productRepository) {
        this.wishlistRepository = wishlistRepository;
        this.userRepository = userRepository;
        this.productRepository = productRepository;
    }

    // 1. Wishlist එකට භාණ්ඩයක් එකතු කිරීම
    public WishlistItemResponseDTO addToWishlist(Long productId, String userEmail) {
        User user = userRepository.findByEmail(userEmail);
        if (user == null) {
            throw new RuntimeException("User not found");
        }

        Product product = productRepository.findById(productId)
                .orElseThrow(() -> new RuntimeException("Product not found"));

        // දැනටමත් Wishlist එකේ තිබේදැයි පරීක්ෂා කිරීම
        if (wishlistRepository.existsByUserIdAndProductId(user.getId(), product.getId())) {
            throw new RuntimeException("Product is already in your wishlist");
        }

        WishlistItem item = new WishlistItem();
        item.setUser(user);
        item.setProduct(product);

        WishlistItem saved = wishlistRepository.save(item);
        return mapToDTO(saved);
    }

    // 2. User ගේ Wishlist එක ලබා ගැනීම
    public List<WishlistItemResponseDTO> getUserWishlist(String userEmail) {
        User user = userRepository.findByEmail(userEmail);
        if (user == null) {
            throw new RuntimeException("User not found");
        }

        return wishlistRepository.findByUserId(user.getId()).stream()
                .map(this::mapToDTO)
                .collect(Collectors.toList());
    }

    // 3. Wishlist එකෙන් භාණ්ඩයක් ඉවත් කිරීම
    public void removeFromWishlist(Long wishlistItemId, String userEmail) {
        User user = userRepository.findByEmail(userEmail);
        if (user == null) {
            throw new RuntimeException("User not found");
        }

        WishlistItem item = wishlistRepository.findById(wishlistItemId)
                .orElseThrow(() -> new RuntimeException("Wishlist item not found"));

        // වෙනත් අයෙකුගේ wishlist item එකක් මකා දැමීම වැළැක්වීම (Security check)
        if (!item.getUser().getId().equals(user.getId())) {
            throw new RuntimeException("You are not authorized to delete this item");
        }

        wishlistRepository.delete(item);
    }

    private WishlistItemResponseDTO mapToDTO(WishlistItem item) {
        WishlistItemResponseDTO dto = new WishlistItemResponseDTO();
        dto.setId(item.getId());
        dto.setProductId(item.getProduct().getId());
        dto.setProductName(item.getProduct().getName());
        dto.setProductPrice(item.getProduct().getPrice());
        dto.setBrand(item.getProduct().getBrand());
        dto.setAddedAt(item.getAddedAt());
        return dto;
    }
}