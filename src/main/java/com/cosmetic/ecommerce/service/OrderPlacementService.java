package com.cosmetic.ecommerce.service;

import com.cosmetic.ecommerce.dto.*;
import com.cosmetic.ecommerce.entity.Order;
import com.cosmetic.ecommerce.entity.User;
import com.cosmetic.ecommerce.repository.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;
import java.util.function.Supplier;

/** Coordinates retries outside the creation transaction, including PostgreSQL unique-key rollback. */
@Service
public class OrderPlacementService {
    private final OrderService service;
    private final OrderRepository orders;
    private final UserRepository users;
    private final TransactionTemplate transactions;
    private final boolean checkoutEnabled;

    public OrderPlacementService(OrderService service, OrderRepository orders, UserRepository users, PlatformTransactionManager manager,
            @org.springframework.beans.factory.annotation.Value("${app.checkout.enabled:true}") boolean checkoutEnabled) {
        this.service = service; this.orders = orders; this.users = users;
        this.checkoutEnabled = checkoutEnabled;
        transactions = new TransactionTemplate(manager);
        transactions.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    public Order placeDirect(String key, DirectOrderRequestDTO request, String email) {
        requireCheckoutEnabled();
        requireExpectedTotal(request.getExpectedTotal());
        String actor = actor(email);
        String scope = hash(canonical("DIRECT", actor, validateKey(key)));
        String fingerprint = hash(canonical(request.getProductId(), request.getQuantity(), request.getName(), request.getPhoneNumber(),
                request.getAddress(), request.getPostalCode(), request.getEmail(), normalize(request.getPaymentMethod()),
                normalize(request.getCouponCode()), request.getExpectedTotal().stripTrailingZeros().toPlainString()));
        return place(scope, fingerprint, () -> service.createDirectOrder(request, email, scope, fingerprint));
    }

    public Order placeCart(String key, CartOrderRequestDTO request, String email) {
        requireCheckoutEnabled();
        requireExpectedTotal(request.getExpectedTotal());
        String actor = actor(email);
        String scope = hash(canonical("CART", actor, validateKey(key)));
        if (request.getCartItems() == null || request.getCartItems().isEmpty()) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Displayed cart items are required");
        java.util.SortedMap<Long, Long> intendedItems = new java.util.TreeMap<>();
        for (var item : request.getCartItems()) {
            if (item == null || item.productId() == null || item.productId() <= 0 || item.quantity() == null || item.quantity() <= 0)
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Displayed cart items are invalid");
            intendedItems.merge(item.productId(), item.quantity().longValue(), Long::sum);
        }
        // A key denotes a single cart checkout attempt. The browser changes it when cart contents change.
        if (request.getCustomerPhone() == null || request.getCustomerPhone().isBlank() || request.getCustomerPhone().length() > 40)
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Customer phone is required");
        String fingerprint = hash(canonical(request.getShippingAddress(), request.getCustomerPhone(), normalize(request.getCouponCode()), intendedItems,
                request.getExpectedTotal().stripTrailingZeros().toPlainString()));
        return place(scope, fingerprint, () -> service.createOrder(email, request.getShippingAddress(), request.getCouponCode(),
                request.getExpectedTotal(), scope, fingerprint, request.getCartItems(), request.getCustomerPhone()));
    }

    private Order place(String keyHash, String fingerprint, Supplier<Order> create) {
        Order existing = lookup(keyHash);
        if (existing != null) return matching(existing, fingerprint);
        try { return transactions.execute(status -> create.get()); }
        catch (RuntimeException failedAttempt) {
            // The failed transaction has fully rolled back before this fresh transaction starts.
            // A concurrent winner may have consumed the last unit or violated our unique request key.
            Order winner = lookup(keyHash);
            if (winner != null) return matching(winner, fingerprint);
            throw failedAttempt;
        }
    }

    private void requireCheckoutEnabled() {
        if (!checkoutEnabled) throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Ordering is temporarily unavailable. Please try again later.") {
            @Override public org.springframework.http.HttpHeaders getHeaders() {
                org.springframework.http.HttpHeaders headers = new org.springframework.http.HttpHeaders();
                headers.set(org.springframework.http.HttpHeaders.RETRY_AFTER, "60");
                return headers;
            }
        };
    }

    private Order lookup(String keyHash) {
        return transactions.execute(status -> orders.findByRequestKeyHash(keyHash).orElse(null));
    }
    private Order matching(Order order, String fingerprint) {
        if (!fingerprint.equals(order.getRequestFingerprint())) throw new ResponseStatusException(HttpStatus.CONFLICT, "This checkout attempt was already used with different details. Start a new attempt.");
        return order;
    }
    private String actor(String email) {
        if (email == null || email.isBlank() || "anonymousUser".equals(email)) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Sign in required");
        }
        User user = users.findByEmail(email);
        if (user == null || user.getId() == null || !user.isEnabled()) throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Account unavailable");
        return "USER:" + user.getId();
    }
    private String validateKey(String key) {
        try {
            if (key == null || key.length() != 36) throw new IllegalArgumentException();
            UUID parsed = UUID.fromString(key);
            if (parsed.version() != 4 || parsed.variant() != 2 || !parsed.toString().equalsIgnoreCase(key)) throw new IllegalArgumentException();
            return parsed.toString();
        } catch (IllegalArgumentException error) { throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "A fresh UUIDv4 Idempotency-Key is required"); }
    }
    private void requireExpectedTotal(java.math.BigDecimal amount) {
        if (amount == null || amount.signum() <= 0 || (long) amount.precision() - amount.scale() > 15 || amount.scale() > 2)
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Displayed order total is invalid");
    }
    private String normalize(String value) { return value == null ? "" : value.trim().toUpperCase(Locale.ROOT); }
    private String canonical(Object... values) {
        StringBuilder output = new StringBuilder();
        for (Object value : values) { String text = value == null ? "" : value.toString(); output.append(text.length()).append(':').append(text); }
        return output.toString();
    }
    private String hash(String value) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8))); }
        catch (java.security.NoSuchAlgorithmException impossible) { throw new IllegalStateException("Digest unavailable"); }
    }
}
