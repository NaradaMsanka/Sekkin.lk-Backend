package com.cosmetic.ecommerce.postgres;

import com.cosmetic.ecommerce.CosmeticEcommerceBackendApplication;
import com.cosmetic.ecommerce.dto.*;
import com.cosmetic.ecommerce.entity.*;
import com.cosmetic.ecommerce.repository.*;
import com.cosmetic.ecommerce.service.*;
import com.cosmetic.ecommerce.util.JwtUtil;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.RepeatedTest;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.server.ResponseStatusException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.sql.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.regex.Pattern;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** Real PostgreSQL, actual application services/security, disposable synthetic data only. */
@EnabledIfEnvironmentVariable(named = "SEKKIN_RUN_POSTGRES_TESTS", matches = "true")
@SpringBootTest(classes = CosmeticEcommerceBackendApplication.class, webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureMockMvc
@ActiveProfiles("isolated-postgresql")
class PostgresReleaseIntegrationTest {
    private static final String MARKER = "SEKKIN_DISPOSABLE_RELEASE_VERIFICATION";
    private static final String SYNTHETIC_KEY = "public-synthetic-postgresql-test-signing-key-never-production-0001";
    private static final String URL = System.getenv("SEKKIN_POSTGRES_TEST_URL");

    @DynamicPropertySource
    static void isolatedConfiguration(DynamicPropertyRegistry properties) throws Exception {
        if (!"true".equals(System.getenv("SEKKIN_RUN_POSTGRES_TESTS")) || URL == null
                || !URL.matches("jdbc:postgresql://127\\.0\\.0\\.1:[0-9]{1,5}/sekkin_release_test"))
            throw new IllegalStateException("Only the explicit disposable loopback PostgreSQL test URL is allowed");
        try (Connection connection = DriverManager.getConnection(URL, "sekkin_test", ""); Statement statement = connection.createStatement()) {
            try (ResultSet result = statement.executeQuery("select shobj_description(oid, 'pg_database') from pg_database where datname = current_database()")) {
                if (!result.next() || !MARKER.equals(result.getString(1))) throw new IllegalStateException("Disposable database marker required before any test DDL");
            }
            // Fail closed rather than drop or overwrite an existing schema/database.
            try (ResultSet result = statement.executeQuery("select count(*) from information_schema.tables where table_schema='public'")) {
                result.next(); if (result.getInt(1) != 0) throw new IllegalStateException("A fresh empty disposable database is required");
            }
            try (var input = PostgresReleaseIntegrationTest.class.getResourceAsStream("/postgres/legacy-schema.sql")) {
                if (input == null) throw new IllegalStateException("Missing synthetic legacy schema fixture");
                statement.execute(new String(input.readAllBytes(), StandardCharsets.UTF_8));
            }
            // Execute the exact reviewed additive migration, then rehearse safe repeated application.
            String migration = Files.readString(Path.of("docs/migrations/2026-11-16-order-session-safety.sql"));
            statement.execute(migration); statement.execute(migration);
        }
        properties.add("spring.datasource.url", () -> URL);
        properties.add("spring.datasource.username", () -> "sekkin_test");
        properties.add("spring.datasource.password", () -> "");
        properties.add("spring.datasource.driver-class-name", () -> "org.postgresql.Driver");
        properties.add("spring.jpa.hibernate.ddl-auto", () -> "validate");
        properties.add("spring.jpa.database-platform", () -> "org.hibernate.dialect.PostgreSQLDialect");
        properties.add("spring.jpa.properties.hibernate.dialect", () -> "org.hibernate.dialect.PostgreSQLDialect");
        properties.add("spring.jpa.show-sql", () -> "false");
        properties.add("security.jwt.secret", () -> SYNTHETIC_KEY);
        properties.add("app.cors.allowed-origins", () -> "https://isolated.sekkin.test");
        properties.add("app.checkout.enabled", () -> "true");
        properties.add("server.forward-headers-strategy", () -> "none");
        properties.add("spring.profiles.active", () -> "isolated-postgresql");
        properties.add("spring.profiles.include", () -> "");
        for (String property : List.of("cloudinary.cloud-name", "cloudinary.api-key", "cloudinary.api-secret"))
            properties.add(property, () -> "");
    }

    @Autowired ProductRepository products; @Autowired OrderRepository orders; @Autowired UserRepository users;
    @Autowired OrderService service; @Autowired OrderPlacementService placement; @Autowired CartService carts;
    @Autowired AccountSecurityService accounts; @Autowired PlatformTransactionManager manager;
    @Autowired JwtUtil jwt; @Autowired PasswordEncoder passwords; @Autowired MockMvc mvc; @Autowired JdbcTemplate jdbc;

    private <T> T transaction(java.util.function.Supplier<T> action) { return new TransactionTemplate(manager).execute(status -> action.get()); }
    private Product product(int stock) {
        Product product = new Product(); product.setName("Synthetic PostgreSQL verification product");
        product.setPrice(new BigDecimal("1000.00")); product.setStockQuantity(stock); return products.saveAndFlush(product);
    }
    private User user(String role) {
        User user = new User(); user.setName("Synthetic PostgreSQL verification account");
        user.setEmail(UUID.randomUUID() + "@example.test"); user.setRole(role);
        user.setPassword(passwords.encode("synthetic-valid-test-password")); return users.saveAndFlush(user);
    }
    private DirectOrderRequestDTO request(Product product) {
        DirectOrderRequestDTO request = new DirectOrderRequestDTO(); request.setProductId(product.getId()); request.setQuantity(1);
        request.setName("Synthetic buyer"); request.setEmail("guest@example.test"); request.setPhoneNumber("test-only-phone");
        request.setAddress("Synthetic test address"); request.setPaymentMethod("COD"); request.setExpectedTotal(new BigDecimal("1350.00")); return request;
    }
    private String bearer(User user) { return "Bearer " + jwt.generateToken(user.getEmail(), user.getRole(), user.getSessionVersion()); }
    private List<Object> race(Callable<?> first, Callable<?> second) throws Exception {
        try (ExecutorService pool = Executors.newFixedThreadPool(2)) {
            CountDownLatch ready = new CountDownLatch(2), start = new CountDownLatch(1);
            List<Future<Object>> results = new ArrayList<>();
            for (Callable<?> action : List.of(first, second)) results.add(pool.submit(() -> {
                ready.countDown(); if (!start.await(10, TimeUnit.SECONDS)) throw new IllegalStateException("Test barrier timeout");
                try { return action.call(); } catch (ResponseStatusException expected) { return expected; }
            }));
            assertTrue(ready.await(10, TimeUnit.SECONDS)); start.countDown();
            return Arrays.asList(results.get(0).get(30, TimeUnit.SECONDS), results.get(1).get(30, TimeUnit.SECONDS));
        }
    }

    @Test void exactMigrationAndSchemaValidationPreserveHistoricalMoneyAndUniqueIndexes() {
        assertTrue(jdbc.queryForObject("select version()", String.class).startsWith("PostgreSQL 17."));
        assertEquals(1, jdbc.queryForObject("select count(*) from pg_indexes where schemaname='public' and tablename='orders' and indexname='uk_orders_request_key_hash' and indexdef like 'CREATE UNIQUE INDEX%'", Integer.class));
        Order history = orders.findById(900001L).orElseThrow(); assertEquals(1000.0, history.getTotalAmount());
        assertEquals(0.0, history.getDeliveryCharge()); assertNull(history.getRequestKeyHash()); assertNull(history.getInventoryReserved());
        User historical = users.findById(900001L).orElseThrow(); assertTrue(historical.isEnabled()); assertEquals(0, historical.getSessionVersion());
        assertEquals(1000.0, jdbc.queryForObject("select price from order_item where id=900001", Double.class));
    }

    @RepeatedTest(3) void actualPostgresqlRowLocksPreventOversellingTheFinalUnit() throws Exception {
        Product product = product(1); long before = orders.count();
        List<Object> results = race(() -> placement.placeDirect(UUID.randomUUID().toString(), request(product), null),
                () -> placement.placeDirect(UUID.randomUUID().toString(), request(product), null));
        assertEquals(1, results.stream().filter(Order.class::isInstance).count());
        assertEquals(1, results.stream().filter(ResponseStatusException.class::isInstance).count());
        assertEquals(before + 1, orders.count()); assertEquals(0, products.findById(product.getId()).orElseThrow().getStockQuantity());
    }

    @ParameterizedTest @ValueSource(ints = {1, 2})
    void actualUniqueConstraintRollbackReplaysOneOrderAndReservesOnce(int stock) throws Exception {
        Product product = product(stock); String key = UUID.randomUUID().toString(); long before = orders.count();
        List<Object> results = race(() -> placement.placeDirect(key, request(product), null), () -> placement.placeDirect(key, request(product), null));
        assertTrue(results.stream().allMatch(Order.class::isInstance)); assertEquals(((Order) results.get(0)).getId(), ((Order) results.get(1)).getId());
        assertEquals(before + 1, orders.count()); assertEquals(stock - 1, products.findById(product.getId()).orElseThrow().getStockQuantity());
        transaction(() -> { Product changed = products.findByIdForUpdate(product.getId()).orElseThrow(); changed.setPrice(new BigDecimal("2000")); return products.save(changed); });
        assertEquals(1350.0, placement.placeDirect(key, request(product), null).getTotalAmount());
        DirectOrderRequestDTO changed = request(product); changed.setAddress("Different synthetic address");
        assertEquals(409, assertThrows(ResponseStatusException.class, () -> placement.placeDirect(key, changed, null)).getStatusCode().value());
    }

    @Test void cartRetriesAndGuestOwnershipAreSafeOnPostgresql() throws Exception {
        User user = user("USER"); Product product = product(4); carts.addProductToCart(user.getEmail(), product.getId(), 1);
        CartOrderRequestDTO request = new CartOrderRequestDTO(); request.setShippingAddress("Synthetic cart address");
        request.setCustomerPhone("test-only-phone"); request.setExpectedTotal(new BigDecimal("1350.00"));
        request.setCartItems(List.of(new CartOrderRequestDTO.CartLine(product.getId(), 1))); String key = UUID.randomUUID().toString();
        List<Object> results = race(() -> placement.placeCart(key, request, user.getEmail()), () -> placement.placeCart(key, request, user.getEmail()));
        assertTrue(results.stream().allMatch(Order.class::isInstance)); assertEquals(((Order) results.get(0)).getId(), ((Order) results.get(1)).getId());
        assertEquals(3, products.findById(product.getId()).orElseThrow().getStockQuantity()); assertTrue(carts.getCart(user.getEmail()).getItems().isEmpty());
        DirectOrderRequestDTO guestRequest = request(product); guestRequest.setEmail(user.getEmail());
        Order guest = placement.placeDirect(UUID.randomUUID().toString(), guestRequest, null); assertNull(guest.getUser());
        assertEquals(403, assertThrows(ResponseStatusException.class, () -> service.getOrderById(guest.getId(), user.getEmail())).getStatusCode().value());
    }

    @Test void cancellationRestoresOnlyOneKnownReservationOnPostgresql() throws Exception {
        Product product = product(2); Order order = placement.placeDirect(UUID.randomUUID().toString(), request(product), null);
        List<Object> results = race(() -> service.updateOrderStatus(order.getId(), "CANCELLED"), () -> service.updateOrderStatus(order.getId(), "CANCELLED"));
        assertTrue(results.stream().allMatch(Order.class::isInstance)); assertEquals(2, products.findById(product.getId()).orElseThrow().getStockQuantity());
        assertFalse(orders.findById(order.getId()).orElseThrow().getInventoryReserved());
    }

    @Test void codEvidenceIsRequiredAndConcurrentCollectionKeepsOneAudit() throws Exception {
        Product product = product(2); Order order = placement.placeDirect(UUID.randomUUID().toString(), request(product), null);
        assertThrows(ResponseStatusException.class, () -> service.recordCodCollection(order.getId(), new BigDecimal("1350"), "synthetic-settlement", "operator@example.test"));
        service.acceptOrder(order.getId(), "synthetic-confirmation", "operator@example.test");
        service.updateOrderStatus(order.getId(), "SHIPPED"); service.updateOrderStatus(order.getId(), "DELIVERED");
        assertEquals("PENDING_COD", orders.findById(order.getId()).orElseThrow().getPaymentStatus());
        List<Object> results = race(() -> service.recordCodCollection(order.getId(), new BigDecimal("1350"), "synthetic-settlement", "first@example.test"),
                () -> service.recordCodCollection(order.getId(), new BigDecimal("1350"), "synthetic-settlement", "second@example.test"));
        assertTrue(results.stream().allMatch(Order.class::isInstance)); Order collected = orders.findById(order.getId()).orElseThrow();
        assertEquals("PAID", collected.getPaymentStatus()); assertEquals("synthetic-settlement", collected.getCodCollectionReference());
        assertNotNull(collected.getCodCollectedAt()); assertTrue(Set.of("first@example.test", "second@example.test").contains(collected.getCodCollectedBy()));
        assertEquals(1350.0, collected.getTotalAmount()); assertEquals(350.0, collected.getDeliveryCharge());
    }

    @Test void merchandiseCouponNeverReducesShippingAndHistoryStaysUnchanged() {
        Product product = product(10); DirectOrderRequestDTO request = request(product); request.setQuantity(3); request.setCouponCode("SAVE500"); request.setExpectedTotal(new BigDecimal("2850"));
        Order order = placement.placeDirect(UUID.randomUUID().toString(), request, null);
        assertEquals(2850.0, order.getTotalAmount()); assertEquals(350.0, order.getDeliveryCharge()); assertEquals(500.0, order.getDiscountAmount());
        Order history = orders.findById(900001L).orElseThrow(); assertEquals(1000.0, history.getTotalAmount()); assertEquals(0.0, history.getDeliveryCharge());
    }

    @Test void actualDatabaseSessionsAndCurrentRolesControlHttpAuthorization() throws Exception {
        User customer = user("USER"), admin = user("ADMIN"); String token = bearer(customer), adminToken = bearer(admin);
        mvc.perform(get("/api/admin/orders")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/admin/orders").header("Authorization", token)).andExpect(status().isForbidden());
        mvc.perform(get("/api/admin/orders").header("Authorization", adminToken)).andExpect(status().isOk());
        mvc.perform(post("/api/admin/users/" + customer.getId() + "/revoke-sessions").header("Authorization", adminToken)).andExpect(status().isNoContent());
        mvc.perform(get("/api/orders").header("Authorization", token)).andExpect(status().isUnauthorized());
        User current = users.findById(customer.getId()).orElseThrow(); String newToken = bearer(current);
        mvc.perform(get("/api/orders").header("Authorization", newToken)).andExpect(status().isOk());
        mvc.perform(post("/api/auth/logout-all").header("Authorization", newToken)).andExpect(status().isNoContent());
        mvc.perform(get("/api/orders").header("Authorization", newToken)).andExpect(status().isUnauthorized());
        mvc.perform(patch("/api/admin/users/" + customer.getId() + "/enabled").header("Authorization", adminToken).contentType("application/json").content("{\"enabled\":false}"))
                .andExpect(status().isNoContent());
        mvc.perform(post("/api/auth/login").contentType("application/json").content("{\"email\":\"" + customer.getEmail() + "\",\"password\":\"synthetic-valid-test-password\"}"))
                .andExpect(status().isUnauthorized());
        transaction(() -> { User account = users.findById(admin.getId()).orElseThrow(); account.setRole("USER"); return users.saveAndFlush(account); });
        mvc.perform(get("/api/admin/orders").header("Authorization", adminToken)).andExpect(status().isForbidden());
    }

    @Test void concurrentSessionRevocationsAreAtomicOnPostgresql() throws Exception {
        User user = user("USER");
        List<Object> results = race(() -> { accounts.revokeSessionsForUser(user.getId()); return true; }, () -> { accounts.revokeSessionsForUser(user.getId()); return true; });
        assertEquals(List.of(true, true), results); assertEquals(2, users.findById(user.getId()).orElseThrow().getSessionVersion());
    }

    @Test void actualSecurityChainAllowsExactCorsAndIdempotencyHeaderOnly() throws Exception {
        mvc.perform(options("/api/orders/direct").header("Origin", "https://isolated.sekkin.test").header("Access-Control-Request-Method", "POST")
                .header("Access-Control-Request-Headers", "content-type,idempotency-key,authorization"))
                .andExpect(status().isOk()).andExpect(header().string("Access-Control-Allow-Origin", "https://isolated.sekkin.test"))
                .andExpect(header().doesNotExist("Access-Control-Allow-Credentials"));
        mvc.perform(options("/api/orders/direct").header("Origin", "https://unapproved.example").header("Access-Control-Request-Method", "POST"))
                .andExpect(status().isForbidden());
    }

    @Test void realLoginAndPublicCodHttpCreationWorkWithoutAnyCardCollection() throws Exception {
        User customer = user("USER");
        var login = mvc.perform(post("/api/auth/login").contentType("application/json")
                .content("{\"email\":\"" + customer.getEmail() + "\",\"password\":\"synthetic-valid-test-password\"}"))
                .andExpect(status().isOk()).andReturn();
        var matcher = Pattern.compile("\\\"token\\\"\\s*:\\s*\\\"([A-Za-z0-9_.-]+)\\\"").matcher(login.getResponse().getContentAsString());
        assertTrue(matcher.find()); String token = matcher.group(1); assertTrue(jwt.validateToken(token));
        mvc.perform(get("/api/orders").header("Authorization", "Bearer " + token)).andExpect(status().isOk());
        Product product = product(3); String key = UUID.randomUUID().toString(); long before = orders.count();
        String body = "{\"productId\":" + product.getId() + ",\"quantity\":1,\"name\":\"Synthetic buyer\",\"phoneNumber\":\"test-phone\","
                + "\"address\":\"Synthetic test address\",\"email\":\"guest@example.test\",\"paymentMethod\":\"COD\",\"expectedTotal\":1350.00}";
        for (int retry = 0; retry < 2; retry++) mvc.perform(post("/api/orders/direct").header("Idempotency-Key", key)
                .contentType("application/json").content(body)).andExpect(status().isOk())
                .andExpect(jsonPath("$.deliveryCharge").value(350)).andExpect(jsonPath("$.totalAmount").value(1350))
                .andExpect(jsonPath("$.paymentStatus").value("PENDING_COD"));
        assertEquals(before + 1, orders.count()); assertEquals(2, products.findById(product.getId()).orElseThrow().getStockQuantity());
        mvc.perform(post("/api/orders/direct").header("Idempotency-Key", UUID.randomUUID().toString()).contentType("application/json")
                .content(body.substring(0, body.length() - 1) + ",\"cardNumber\":\"synthetic-forbidden-card-data\"}"))
                .andExpect(status().isBadRequest());
        assertEquals(before + 1, orders.count());
    }

    @Test void realOrderStatusAndOwnerDetailSerializeWithoutHibernateProxies() throws Exception {
        User customer = user("USER");
        User admin = user("ADMIN");
        Product product = product(3);
        Order order = placement.placeDirect(UUID.randomUUID().toString(), request(product), customer.getEmail());
        service.acceptOrder(order.getId(), "isolated-acceptance-reference", admin.getEmail());
        mvc.perform(patch("/api/orders/" + order.getId() + "/status").param("status", "SHIPPED")
                .header("Authorization", bearer(admin)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("SHIPPED"))
                .andExpect(jsonPath("$.deliveryCharge").value(350))
                .andExpect(jsonPath("$.orderItems").doesNotExist());
        mvc.perform(get("/api/orders/" + order.getId()).header("Authorization", bearer(customer)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.id").value(order.getId()))
                .andExpect(jsonPath("$.user").doesNotExist());
    }
}
