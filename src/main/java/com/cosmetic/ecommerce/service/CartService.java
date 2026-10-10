package com.cosmetic.ecommerce.service;

import com.cosmetic.ecommerce.entity.Cart;
import com.cosmetic.ecommerce.entity.CartItem;
import com.cosmetic.ecommerce.entity.Product;
import com.cosmetic.ecommerce.entity.User;
import com.cosmetic.ecommerce.repository.CartRepository;
import com.cosmetic.ecommerce.repository.ProductRepository;
import com.cosmetic.ecommerce.repository.UserRepository;
import org.springframework.stereotype.Service;

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
        // 1. User සහ Product Database එකේ ඉන්නවද බලනවා
        User user = userRepository.findByEmail(email);
        if (user == null) {
            throw new RuntimeException("User not found");
        }
        Product product = productRepository.findById(productId).orElseThrow(() -> new RuntimeException("Product not found"));

        // 2. ගානට Stock තියෙනවද කියලා බලනවා (Validation)
        if (product.getStockQuantity() < quantity) {
            throw new RuntimeException("Not enough stock available. Only " + product.getStockQuantity() + " left.");
        }

        // 3. User ට කලින් Cart එකක් තියෙනවද බලනවා. නැත්නම් අලුත් එකක් හදනවා
        Cart cart = cartRepository.findByUserEmail(email).orElseGet(() -> {
            Cart newCart = new Cart();
            newCart.setUser(user);
            return newCart;
        });

        // 4. කලින් මේ භාණ්ඩයම Cart එකට දාලද බලනවා
        Optional<CartItem> existingItem = cart.getItems().stream()
                .filter(item -> item.getProduct().getId().equals(productId))
                .findFirst();

        if (existingItem.isPresent()) {
            CartItem item = existingItem.get();
            item.setQuantity(item.getQuantity() + quantity);
            item.setPrice(item.getQuantity() * product.getPrice().doubleValue());
        } else {
            CartItem newItem = new CartItem();
            newItem.setCart(cart);
            newItem.setProduct(product);
            newItem.setQuantity(quantity);
            newItem.setPrice(quantity * product.getPrice().doubleValue());
            cart.getItems().add(newItem);
        }

        // 5. මුළු මුදල (Total Price) එකතු කරනවා
        double total = cart.getItems().stream().mapToDouble(CartItem::getPrice).sum();
        cart.setTotalPrice(total);

        return cartRepository.save(cart);
    }

    public Cart getCart(String email) {
        return cartRepository.findByUserEmail(email).orElseThrow(() -> new RuntimeException("Cart is empty"));
    }

    // --- අලුතින් එකතු කළ කොටස (Day 15) ---

    // 1. Cart එකේ තියෙන භාණ්ඩයක ප්‍රමාණය (Quantity) වෙනස් කිරීම
    public Cart updateItemQuantity(String email, Long productId, Integer newQuantity) {
        Cart cart = getCart(email);

        Product product = productRepository.findById(productId)
                .orElseThrow(() -> new RuntimeException("Product not found"));

        if (product.getStockQuantity() < newQuantity) {
            throw new RuntimeException("Not enough stock available.");
        }

        Optional<CartItem> existingItem = cart.getItems().stream()
                .filter(item -> item.getProduct().getId().equals(productId))
                .findFirst();

        if (existingItem.isPresent()) {
            CartItem item = existingItem.get();
            item.setQuantity(newQuantity);
            item.setPrice(newQuantity * product.getPrice().doubleValue());
        } else {
            throw new RuntimeException("Item not found in cart");
        }

        double total = cart.getItems().stream().mapToDouble(CartItem::getPrice).sum();
        cart.setTotalPrice(total);

        return cartRepository.save(cart);
    }

    // 2. Cart එකෙන් එක භාණ්ඩයක් අයින් කිරීම
    public Cart removeItemFromCart(String email, Long productId) {
        Cart cart = getCart(email);

        cart.getItems().removeIf(item -> item.getProduct().getId().equals(productId));

        double total = cart.getItems().stream().mapToDouble(CartItem::getPrice).sum();
        cart.setTotalPrice(total);

        return cartRepository.save(cart);
    }

    // 3. Cart එක සම්පූර්ණයෙන්ම හිස් කිරීම
    public void clearCart(String email) {
        Cart cart = getCart(email);
        cart.getItems().clear();
        cart.setTotalPrice(0.0);
        cartRepository.save(cart);
    }
}