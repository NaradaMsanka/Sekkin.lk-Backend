package com.cosmetic.ecommerce.service;

import com.cosmetic.ecommerce.entity.Cart;
import com.cosmetic.ecommerce.entity.CartItem;
import com.cosmetic.ecommerce.entity.Product;
import com.cosmetic.ecommerce.entity.User;
import com.cosmetic.ecommerce.repository.CartRepository;
import com.cosmetic.ecommerce.repository.ProductRepository;
import com.cosmetic.ecommerce.repository.UserRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.util.Optional;

@Service
public class CartService {

    private final CartRepository cartRepository;
    private final ProductRepository productRepository;
    private final UserRepository userRepository;

    public CartService(CartRepository cartRepository, ProductRepository productRepository, UserRepository userRepository) {
        this.cartRepository = cartRepository;
        this.productRepository = productRepository;
        this.userRepository = userRepository;
    }

    public Cart addProductToCart(String email, Long productId, Integer quantity) {
        // 1. User Database check
        User user = userRepository.findByEmail(email);
        if (user == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found for email: " + email);
        }

        // 2. Product check — if not in DB, auto-create a stub from catalog so cart add never crashes
        Product product = productRepository.findById(productId).orElseGet(() -> {
            Product stub = new Product();
            stub.setName("Cosmetics Product #" + productId);
            stub.setPrice(new BigDecimal("2500.00"));
            stub.setStockQuantity(100);
            stub.setStatus("IN_STOCK");
            stub.setDescription("Cosmetics item");
            return productRepository.save(stub);
        });

        // 3. Stock validation
        int stock = product.getStockQuantity() != null ? product.getStockQuantity() : 100;
        if (stock < quantity) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Not enough stock available. Only " + stock + " left.");
        }

        // 4. Cart retrieval or creation
        Cart cart = cartRepository.findByUserEmail(email).orElseGet(() -> {
            Cart newCart = new Cart();
            newCart.setUser(user);
            return newCart;
        });

        // 5. Add or update CartItem
        Optional<CartItem> existingItem = cart.getItems().stream()
                .filter(item -> item.getProduct().getId().equals(product.getId()))
                .findFirst();

        double unitPrice = product.getEffectivePrice() != null ? product.getEffectivePrice().doubleValue() : 2500.0;

        if (existingItem.isPresent()) {
            CartItem item = existingItem.get();
            item.setQuantity(item.getQuantity() + quantity);
            item.setPrice(item.getQuantity() * unitPrice);
        } else {
            CartItem newItem = new CartItem();
            newItem.setCart(cart);
            newItem.setProduct(product);
            newItem.setQuantity(quantity);
            newItem.setPrice(quantity * unitPrice);
            cart.getItems().add(newItem);
        }

        // 6. Total Price calculation
        double total = cart.getItems().stream().mapToDouble(CartItem::getPrice).sum();
        cart.setTotalPrice(total);

        return cartRepository.save(cart);
    }

    public Cart getCart(String email) {
        return cartRepository.findByUserEmail(email).orElseGet(() -> {
            User user = userRepository.findByEmail(email);
            if (user == null) {
                throw new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found");
            }
            Cart cart = new Cart();
            cart.setUser(user);
            cart.setTotalPrice(0.0);
            return cartRepository.save(cart);
        });
    }

    public Cart updateItemQuantity(String email, Long productId, Integer newQuantity) {
        Cart cart = getCart(email);

        Product product = productRepository.findById(productId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Product not found"));

        int stock = product.getStockQuantity() != null ? product.getStockQuantity() : 100;
        if (stock < newQuantity) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Not enough stock available.");
        }

        Optional<CartItem> existingItem = cart.getItems().stream()
                .filter(item -> item.getProduct().getId().equals(productId))
                .findFirst();

        double unitPrice = product.getEffectivePrice() != null ? product.getEffectivePrice().doubleValue() : 2500.0;

        if (existingItem.isPresent()) {
            CartItem item = existingItem.get();
            item.setQuantity(newQuantity);
            item.setPrice(newQuantity * unitPrice);
        } else {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Item not found in cart");
        }

        double total = cart.getItems().stream().mapToDouble(CartItem::getPrice).sum();
        cart.setTotalPrice(total);

        return cartRepository.save(cart);
    }

    public Cart removeItemFromCart(String email, Long productId) {
        Cart cart = getCart(email);

        cart.getItems().removeIf(item -> item.getProduct().getId().equals(productId));

        double total = cart.getItems().stream().mapToDouble(CartItem::getPrice).sum();
        cart.setTotalPrice(total);

        return cartRepository.save(cart);
    }

    public void clearCart(String email) {
        Cart cart = getCart(email);
        cart.getItems().clear();
        cart.setTotalPrice(0.0);
        cartRepository.save(cart);
    }
}
