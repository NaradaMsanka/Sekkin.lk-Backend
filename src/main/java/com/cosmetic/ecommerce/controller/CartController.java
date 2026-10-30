package com.cosmetic.ecommerce.controller;

import com.cosmetic.ecommerce.dto.AddToCartRequest;
import com.cosmetic.ecommerce.entity.Cart;
import com.cosmetic.ecommerce.service.CartService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/cart")
public class CartController {

    private final CartService cartService;

    public CartController(CartService cartService) {
        this.cartService = cartService;
    }

    // Token එකෙන් දැනට ලොග් වෙලා ඉන්න කෙනාගේ Email එක ගන්න Method එක
    private String getLoggedInUserEmail() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        return auth.getName();
    }

    // POST /api/cart/items (Cart එකට දාන්න)
    @PostMapping("/items")
    public ResponseEntity<Cart> addToCart(@RequestBody AddToCartRequest request) {
        String email = getLoggedInUserEmail();
        Cart updatedCart = cartService.addProductToCart(email, request.getProductId(), request.getQuantity());
        return ResponseEntity.ok(updatedCart);
    }

    // GET /api/cart (Cart එකේ තියෙන බඩු බලන්න)
    @GetMapping
    public ResponseEntity<Cart> getCart() {
        String email = getLoggedInUserEmail();
        return ResponseEntity.ok(cartService.getCart(email));
    }
    // PUT /api/cart/items/{productId} (Quantity එක Update කරන්න)
    @PutMapping("/items/{productId}")
    public ResponseEntity<Cart> updateItemQuantity(
            @PathVariable Long productId,
            @RequestParam Integer quantity) {
        String email = getLoggedInUserEmail();
        Cart updatedCart = cartService.updateItemQuantity(email, productId, quantity);
        return ResponseEntity.ok(updatedCart);
    }

    // DELETE /api/cart/items/{productId} (භාණ්ඩය Cart එකෙන් අයින් කරන්න)
    @DeleteMapping("/items/{productId}")
    public ResponseEntity<Cart> removeItemFromCart(@PathVariable Long productId) {
        String email = getLoggedInUserEmail();
        Cart updatedCart = cartService.removeItemFromCart(email, productId);
        return ResponseEntity.ok(updatedCart);
    }
}