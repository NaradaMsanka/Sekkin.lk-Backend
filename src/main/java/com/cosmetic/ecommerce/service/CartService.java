package com.cosmetic.ecommerce.service;

import com.cosmetic.ecommerce.entity.*;
import com.cosmetic.ecommerce.repository.*;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import java.math.BigDecimal;

@Service
@Transactional
public class CartService {
    private final CartRepository cartRepository;
    private final ProductRepository productRepository;
    private final UserRepository userRepository;

    public CartService(CartRepository carts, ProductRepository products, UserRepository users) {
        cartRepository = carts; productRepository = products; userRepository = users;
    }

    public Cart addProductToCart(String email, Long productId, Integer quantity) {
        requireQuantity(quantity);
        User user = userRepository.findByEmailForUpdate(email);
        if (user == null) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Account not found");
        Product product = requireProduct(productId);
        Cart cart = cartRepository.findByUserEmail(email).orElseGet(() -> {
            Cart created = new Cart(); created.setUser(user); return created;
        });
        CartItem item = cart.getItems().stream().filter(line -> line.getProduct().getId().equals(productId)).findFirst().orElse(null);
        long combined = quantity;
        if (item != null) { requireQuantity(item.getQuantity()); combined += item.getQuantity(); }
        requireStock(product, combined);
        if (item == null) {
            item = new CartItem(); item.setCart(cart); item.setProduct(product); cart.getItems().add(item);
        }
        item.setQuantity((int) combined);
        recalculate(cart);
        return cartRepository.save(cart);
    }

    public Cart getCart(String email) {
        User user = userRepository.findByEmailForUpdate(email);
        if (user == null) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Account not found");
        Cart cart = cartRepository.findByUserEmail(email).orElseGet(() -> {
            Cart created = new Cart(); created.setUser(user); created.setTotalPrice(0.0);
            return cartRepository.save(created);
        });
        recalculate(cart);
        return cart;
    }

    public Cart updateItemQuantity(String email, Long productId, Integer quantity) {
        requireQuantity(quantity);
        Cart cart = getCart(email);
        Product product = requireProduct(productId);
        requireStock(product, quantity);
        CartItem item = cart.getItems().stream().filter(line -> line.getProduct().getId().equals(productId)).findFirst()
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Item not found in cart"));
        item.setQuantity(quantity);
        recalculate(cart);
        return cartRepository.save(cart);
    }

    public Cart removeItemFromCart(String email, Long productId) {
        Cart cart = getCart(email);
        cart.getItems().removeIf(item -> item.getProduct().getId().equals(productId));
        recalculate(cart);
        return cartRepository.save(cart);
    }

    public void clearCart(String email) {
        Cart cart = getCart(email); cart.getItems().clear(); cart.setTotalPrice(0.0); cartRepository.save(cart);
    }

    private Product requireProduct(Long id) {
        if (id == null || id <= 0) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Product ID is required");
        Product product = productRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Product not found"));
        price(product);
        return product;
    }

    private BigDecimal price(Product product) {
        try { return OrderPricing.unitPrice(product); }
        catch (IllegalArgumentException error) { throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Product price is invalid"); }
    }

    private void requireQuantity(Integer quantity) {
        if (quantity == null || quantity <= 0) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Quantity must be a positive integer");
    }

    private void requireStock(Product product, long quantity) {
        if (quantity > Integer.MAX_VALUE || product.getStockQuantity() == null || quantity > product.getStockQuantity()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Not enough stock available");
        }
    }

    private void recalculate(Cart cart) {
        BigDecimal subtotal = BigDecimal.ZERO;
        for (CartItem item : cart.getItems()) {
            requireQuantity(item.getQuantity());
            BigDecimal line = price(item.getProduct()).multiply(BigDecimal.valueOf(item.getQuantity()));
            item.setPrice(line.doubleValue()); subtotal = subtotal.add(line);
        }
        // Cart.totalPrice remains merchandise only; OrderPricing adds delivery once at checkout.
        cart.setTotalPrice(subtotal.doubleValue());
    }
}
