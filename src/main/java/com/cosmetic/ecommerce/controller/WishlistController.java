package com.cosmetic.ecommerce.controller;

import com.cosmetic.ecommerce.dto.WishlistItemResponseDTO;
import com.cosmetic.ecommerce.service.WishlistService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/wishlist")
public class WishlistController {

    private final WishlistService wishlistService;

    public WishlistController(WishlistService wishlistService) {
        this.wishlistService = wishlistService;
    }

    // POST /api/wishlist
    @PostMapping
    public ResponseEntity<WishlistItemResponseDTO> addToWishlist(@RequestBody Map<String, Long> request,
                                                                 Authentication authentication) {
        String userEmail = authentication.getName();
        Long productId = request.get("productId");
        WishlistItemResponseDTO response = wishlistService.addToWishlist(productId, userEmail);
        return new ResponseEntity<>(response, HttpStatus.CREATED);
    }

    // GET /api/wishlist
    @GetMapping
    public ResponseEntity<List<WishlistItemResponseDTO>> getWishlist(Authentication authentication) {
        String userEmail = authentication.getName();
        return ResponseEntity.ok(wishlistService.getUserWishlist(userEmail));
    }

    // DELETE /api/wishlist/{id}
    @DeleteMapping("/{id}")
    public ResponseEntity<String> removeFromWishlist(@PathVariable Long id,
                                                     Authentication authentication) {
        String userEmail = authentication.getName();
        wishlistService.removeFromWishlist(id, userEmail);
        return ResponseEntity.ok("Item removed from wishlist successfully");
    }
}