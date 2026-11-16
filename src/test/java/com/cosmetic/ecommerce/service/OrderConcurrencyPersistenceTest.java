package com.cosmetic.ecommerce.service;

import com.cosmetic.ecommerce.dto.*;
import com.cosmetic.ecommerce.entity.*;
import com.cosmetic.ecommerce.repository.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.persistence.autoconfigure.EntityScan;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.context.annotation.*;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.transaction.*;
import org.springframework.transaction.annotation.*;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.server.ResponseStatusException;
import java.math.BigDecimal;
import java.util.*;
import java.util.concurrent.*;
import static org.junit.jupiter.api.Assertions.*;

// Embedded replacement H2 only. No hosted database, application startup, credentials or production orders.
@DataJpaTest(properties = {"spring.jpa.hibernate.ddl-auto=create-drop", "spring.jpa.show-sql=false",
        "spring.jpa.database-platform=org.hibernate.dialect.H2Dialect", "spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.H2Dialect"})
@ContextConfiguration(classes = OrderConcurrencyPersistenceTest.PersistenceConfiguration.class)
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class OrderConcurrencyPersistenceTest {
    @Configuration
    @EnableJpaRepositories(basePackageClasses = OrderRepository.class)
    @EntityScan(basePackageClasses = Order.class)
    @Import({OrderService.class, CartService.class, OrderPlacementService.class})
    static class PersistenceConfiguration {}

    @Autowired ProductRepository products; @Autowired OrderRepository orders; @Autowired UserRepository users;
    @Autowired CartRepository carts; @Autowired OrderService service; @Autowired OrderPlacementService placement;
    @Autowired PlatformTransactionManager manager;

    private <T> T tx(java.util.function.Supplier<T> action) { return new TransactionTemplate(manager).execute(status -> action.get()); }
    private Product product(int stock) {
        Product product = new Product(); product.setName("Isolated order safety fixture"); product.setPrice(new BigDecimal("1000")); product.setStockQuantity(stock);
        return products.saveAndFlush(product);
    }
    private DirectOrderRequestDTO request(Product product) {
        DirectOrderRequestDTO request = new DirectOrderRequestDTO(); request.setProductId(product.getId()); request.setQuantity(1);
        request.setName("Isolated buyer"); request.setEmail("guest@example.test"); request.setPhoneNumber("test phone");
        request.setAddress("Isolated address"); request.setPaymentMethod("COD"); request.setExpectedTotal(new BigDecimal("1350.00")); return request;
    }
    private List<Object> simultaneously(Callable<Order> first, Callable<Order> second) throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(2); CountDownLatch ready = new CountDownLatch(2), start = new CountDownLatch(1);
        try {
            List<Future<Object>> results = new ArrayList<>();
            for (Callable<Order> task : List.of(first, second)) results.add(pool.submit(() -> {
                ready.countDown(); if (!start.await(5, TimeUnit.SECONDS)) throw new IllegalStateException("Test start timeout");
                try { return task.call(); } catch (ResponseStatusException expected) { return expected; }
            }));
            assertTrue(ready.await(5, TimeUnit.SECONDS)); start.countDown();
            return List.of(results.get(0).get(15, TimeUnit.SECONDS), results.get(1).get(15, TimeUnit.SECONDS));
        } finally { pool.shutdownNow(); assertTrue(pool.awaitTermination(5, TimeUnit.SECONDS)); }
    }

    @Test void twoIndependentCheckoutsCannotOversellTheLastUnit() throws Exception {
        Product product = product(1); long before = orders.count();
        List<Object> results = simultaneously(() -> placement.placeDirect(UUID.randomUUID().toString(), request(product), null),
                () -> placement.placeDirect(UUID.randomUUID().toString(), request(product), null));
        assertEquals(1, results.stream().filter(Order.class::isInstance).count());
        assertEquals(1, results.stream().filter(ResponseStatusException.class::isInstance).count());
        assertEquals(0, products.findById(product.getId()).orElseThrow().getStockQuantity()); assertEquals(before + 1, orders.count());
    }

    @ParameterizedTest @ValueSource(ints = {1, 2})
    void concurrentIdenticalRetriesCreateOneOrderAndReserveOnce(int stock) throws Exception {
        Product product = product(stock); long before = orders.count(); String key = UUID.randomUUID().toString();
        List<Object> results = simultaneously(() -> placement.placeDirect(key, request(product), null), () -> placement.placeDirect(key, request(product), null));
        assertTrue(results.stream().allMatch(Order.class::isInstance));
        assertEquals(((Order) results.get(0)).getId(), ((Order) results.get(1)).getId());
        assertEquals(stock - 1, products.findById(product.getId()).orElseThrow().getStockQuantity()); assertEquals(before + 1, orders.count());
        // Retries return the original receipt instead of repricing or consuming stock again.
        tx(() -> { Product changed = products.findByIdForUpdate(product.getId()).orElseThrow(); changed.setPrice(new BigDecimal("2000")); return products.save(changed); });
        Order replay = placement.placeDirect(key, request(product), null);
        assertEquals(1350.0, replay.getTotalAmount()); assertEquals(350.0, replay.getDeliveryCharge());
    }

    @Test void changedIntentOrInvalidKeysCannotReuseAnAttempt() {
        Product product = product(4); String key = UUID.randomUUID().toString(); Order first = placement.placeDirect(key, request(product), null);
        DirectOrderRequestDTO altered = request(product); altered.setAddress("Different address");
        assertEquals(409, assertThrows(ResponseStatusException.class, () -> placement.placeDirect(key, altered, null)).getStatusCode().value());
        assertEquals(3, products.findById(product.getId()).orElseThrow().getStockQuantity());
        for (String bad : List.of("short", "00000000-0000-1000-8000-000000000000", "00000000-0000-4000-0000-000000000000"))
            assertEquals(400, assertThrows(ResponseStatusException.class, () -> placement.placeDirect(bad, request(product), null)).getStatusCode().value());
        assertEquals(first.getId(), placement.placeDirect(key, request(product), null).getId());
    }

    @Test void guestEmailNeverCreatesAccountOwnershipAndDifferentActorsHaveIndependentKeys() {
        Product product = product(4); User user = account(); DirectOrderRequestDTO request = request(product); request.setEmail(user.getEmail());
        String key = UUID.randomUUID().toString(); Order guest = placement.placeDirect(key, request, null);
        assertNull(guest.getUser()); Order signedIn = placement.placeDirect(key, request, user.getEmail());
        assertNotEquals(guest.getId(), signedIn.getId());
        assertEquals(user.getId(), tx(() -> orders.findById(signedIn.getId()).orElseThrow().getUser().getId()));
    }

    private User account() {
        User user = new User(); user.setName("Isolated account"); user.setEmail(UUID.randomUUID() + "@example.test");
        user.setPassword("synthetic-only"); user.setRole("USER"); return users.saveAndFlush(user);
    }
    private CartOrderRequestDTO cartRequest(User user, Product product) {
        tx(() -> {
            Cart cart = new Cart(); cart.setUser(users.findById(user.getId()).orElseThrow()); CartItem item = new CartItem();
            item.setCart(cart); item.setProduct(products.findById(product.getId()).orElseThrow()); item.setQuantity(1); item.setPrice(1000.0);
            cart.getItems().add(item); return carts.saveAndFlush(cart);
        });
        CartOrderRequestDTO request = new CartOrderRequestDTO(); request.setShippingAddress("Isolated cart address");
        request.setCustomerPhone("test phone");
        request.setExpectedTotal(new BigDecimal("1350")); request.setCartItems(List.of(new CartOrderRequestDTO.CartLine(product.getId(), 1))); return request;
    }

    @Test void authenticatedCartRetryReplaysAfterCartIsCleared() {
        User user = account(); Product product = product(2); CartOrderRequestDTO request = cartRequest(user, product); String key = UUID.randomUUID().toString();
        Order first = placement.placeCart(key, request, user.getEmail()); Order replay = placement.placeCart(key, request, user.getEmail());
        assertEquals(first.getId(), replay.getId()); assertEquals(1, products.findById(product.getId()).orElseThrow().getStockQuantity());
        assertEquals("test phone", first.getCustomerPhone()); assertEquals(user.getEmail(), first.getCustomerEmail());
        assertEquals(0, tx(() -> carts.findByUserEmail(user.getEmail()).orElseThrow().getItems().size()));
    }

    @Test void changedCartWithTheSamePriceNeedsFreshConsent() {
        User user = account(); Product product = product(2), another = product(2);
        CartOrderRequestDTO request = cartRequest(user, product); request.setCartItems(List.of(new CartOrderRequestDTO.CartLine(another.getId(), 1)));
        assertEquals(409, assertThrows(ResponseStatusException.class, () -> placement.placeCart(UUID.randomUUID().toString(), request, user.getEmail())).getStatusCode().value());
        assertEquals(2, products.findById(product.getId()).orElseThrow().getStockQuantity());
        assertEquals(1, tx(() -> carts.findByUserEmail(user.getEmail()).orElseThrow().getItems().size()));
    }

    @Test void concurrentCancellationRestoresOnlyOneReservation() throws Exception {
        Product product = product(2); Order created = placement.placeDirect(UUID.randomUUID().toString(), request(product), null);
        List<Object> results = simultaneously(() -> service.updateOrderStatus(created.getId(), "CANCELLED"), () -> service.updateOrderStatus(created.getId(), "CANCELLED"));
        assertTrue(results.stream().allMatch(Order.class::isInstance));
        assertEquals(2, products.findById(product.getId()).orElseThrow().getStockQuantity());
        Order saved = orders.findById(created.getId()).orElseThrow(); assertFalse(saved.getInventoryReserved()); assertEquals(1350.0, saved.getTotalAmount());
    }

    @Test void extremeDisplayedTotalsAreRejectedWithoutCreatingRecords() {
        Product product = product(2); DirectOrderRequestDTO request = request(product); request.setExpectedTotal(new BigDecimal("1e100000000"));
        long before = orders.count();
        assertEquals(400, assertThrows(ResponseStatusException.class, () -> placement.placeDirect(UUID.randomUUID().toString(), request, null)).getStatusCode().value());
        assertEquals(before, orders.count()); assertEquals(2, products.findById(product.getId()).orElseThrow().getStockQuantity());
    }

    @Test void concurrentAcceptanceKeepsOneConfirmationAuditAndDoesNotChargePayment() throws Exception {
        Product product = product(2); Order created = placement.placeDirect(UUID.randomUUID().toString(), request(product), null);
        List<Object> results = simultaneously(() -> service.acceptOrder(created.getId(), "manual-confirmation", "first-admin@example.test"),
                () -> service.acceptOrder(created.getId(), "manual-confirmation", "second-admin@example.test"));
        assertTrue(results.stream().allMatch(Order.class::isInstance));
        Order saved = orders.findById(created.getId()).orElseThrow(); assertEquals("PROCESSING", saved.getStatus());
        assertEquals("PENDING_COD", saved.getPaymentStatus()); assertNotNull(saved.getAcceptedAt());
        assertTrue(Set.of("first-admin@example.test", "second-admin@example.test").contains(saved.getAcceptedBy()));
        assertEquals(1, products.findById(product.getId()).orElseThrow().getStockQuantity());
    }

    @Test void concurrentCollectionConfirmsOneAuditWithoutOverwritingItsOperator() throws Exception {
        Product product = product(2); Order created = placement.placeDirect(UUID.randomUUID().toString(), request(product), null);
        service.acceptOrder(created.getId(), "manual-confirmation", "admin@example.test");
        service.updateOrderStatus(created.getId(), "SHIPPED"); service.updateOrderStatus(created.getId(), "DELIVERED");
        List<Object> results = simultaneously(() -> service.recordCodCollection(created.getId(), new BigDecimal("1350"), "courier-settlement-001", "first-admin@example.test"),
                () -> service.recordCodCollection(created.getId(), new BigDecimal("1350"), "courier-settlement-001", "second-admin@example.test"));
        assertTrue(results.stream().allMatch(Order.class::isInstance));
        Order saved = orders.findById(created.getId()).orElseThrow(); assertEquals("PAID", saved.getPaymentStatus());
        assertNotNull(saved.getCodCollectedAt()); assertEquals("courier-settlement-001", saved.getCodCollectionReference());
        assertTrue(Set.of("first-admin@example.test", "second-admin@example.test").contains(saved.getCodCollectedBy()));
        assertEquals(1350.0, saved.getTotalAmount()); assertEquals(350.0, saved.getDeliveryCharge());
        assertEquals(1, products.findById(product.getId()).orElseThrow().getStockQuantity());
    }
}
