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
            String passwordResetMigration = Files.readString(Path.of("docs/migrations/2026-11-22-password-reset.sql"));
            statement.execute(passwordResetMigration); statement.execute(passwordResetMigration);
            String promotionsMigration = Files.readString(Path.of("docs/migrations/2026-11-24-product-promotions.sql"));
            statement.execute(promotionsMigration); statement.execute(promotionsMigration);
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
        properties.add("app.password-reset.enabled", () -> "false");
        properties.add("app.password-reset.frontend-url", () -> "https://isolated.sekkin.test");
        properties.add("spring.mail.host", () -> "127.0.0.1");
        properties.add("spring.mail.port", () -> "1");
        properties.add("spring.mail.username", () -> "synthetic");
        properties.add("spring.mail.password", () -> "");
        properties.add("server.forward-headers-strategy", () -> "none");
        properties.add("spring.profiles.active", () -> "isolated-postgresql");
        properties.add("spring.profiles.include", () -> "");
        for (String property : List.of("cloudinary.cloud-name", "cloudinary.api-key", "cloudinary.api-secret"))
            properties.add(property, () -> "");
    }

    @Autowired ProductRepository products; @Autowired OrderRepository orders; @Autowired UserRepository users;
    @Autowired OrderService service; @Autowired OrderPlacementService placement; @Autowired CartService carts;
    @Autowired AccountSecurityService accounts; @Autowired PlatformTransactionManager manager;
    @Autowired PasswordResetService passwordResets;
    @Autowired PromotionService promotions;
    @Autowired JwtUtil jwt; @Autowired PasswordEncoder passwords; @Autowired MockMvc mvc; @Autowired JdbcTemplate jdbc;
    private User checkoutUser;
    @org.junit.jupiter.api.BeforeEach void signInCheckoutCustomer() { checkoutUser = user("USER"); }

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
        request.setAddress("Synthetic test address"); request.setPaymentMethod("COD"); request.setExpectedTotal(new BigDecimal("1400.00")); return request;
    }
    private String bearer(User user) { return "Bearer " + jwt.generateToken(user.getEmail(), user.getRole(), user.getSessionVersion()); }
    private String resetToken(User user) {
        PasswordResetService.Delivery delivery = passwordResets.issue(user.getEmail());
        assertNotNull(delivery); assertEquals(user.getEmail(), delivery.email());
        assertTrue(delivery.url().matches("https://[^?#]+/reset-password#token=[A-Za-z0-9_-]{43}"));
        return delivery.url().substring(delivery.url().indexOf("#token=") + "#token=".length());
    }
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
        List<Object> results = race(() -> placement.placeDirect(UUID.randomUUID().toString(), request(product), checkoutUser.getEmail()),
                () -> placement.placeDirect(UUID.randomUUID().toString(), request(product), checkoutUser.getEmail()));
        assertEquals(1, results.stream().filter(Order.class::isInstance).count());
        assertEquals(1, results.stream().filter(ResponseStatusException.class::isInstance).count());
        assertEquals(before + 1, orders.count()); assertEquals(0, products.findById(product.getId()).orElseThrow().getStockQuantity());
    }

    @ParameterizedTest @ValueSource(ints = {1, 2})
    void actualUniqueConstraintRollbackReplaysOneOrderAndReservesOnce(int stock) throws Exception {
        Product product = product(stock); String key = UUID.randomUUID().toString(); long before = orders.count();
        List<Object> results = race(() -> placement.placeDirect(key, request(product), checkoutUser.getEmail()), () -> placement.placeDirect(key, request(product), checkoutUser.getEmail()));
        assertTrue(results.stream().allMatch(Order.class::isInstance)); assertEquals(((Order) results.get(0)).getId(), ((Order) results.get(1)).getId());
        assertEquals(before + 1, orders.count()); assertEquals(stock - 1, products.findById(product.getId()).orElseThrow().getStockQuantity());
        transaction(() -> { Product changed = products.findByIdForUpdate(product.getId()).orElseThrow(); changed.setPrice(new BigDecimal("2000")); return products.save(changed); });
        assertEquals(1400.0, placement.placeDirect(key, request(product), checkoutUser.getEmail()).getTotalAmount());
        DirectOrderRequestDTO changed = request(product); changed.setAddress("Different synthetic address");
        assertEquals(409, assertThrows(ResponseStatusException.class, () -> placement.placeDirect(key, changed, checkoutUser.getEmail())).getStatusCode().value());
    }

    @Test void cartRetriesAndAuthenticatedOwnershipAreSafeOnPostgresql() throws Exception {
        User user = user("USER"); Product product = product(4); carts.addProductToCart(user.getEmail(), product.getId(), 1);
        CartOrderRequestDTO request = new CartOrderRequestDTO(); request.setShippingAddress("Synthetic cart address");
        request.setCustomerPhone("test-only-phone"); request.setExpectedTotal(new BigDecimal("1400.00"));
        request.setCartItems(List.of(new CartOrderRequestDTO.CartLine(product.getId(), 1))); String key = UUID.randomUUID().toString();
        List<Object> results = race(() -> placement.placeCart(key, request, user.getEmail()), () -> placement.placeCart(key, request, user.getEmail()));
        assertTrue(results.stream().allMatch(Order.class::isInstance)); assertEquals(((Order) results.get(0)).getId(), ((Order) results.get(1)).getId());
        assertEquals(3, products.findById(product.getId()).orElseThrow().getStockQuantity()); assertTrue(carts.getCart(user.getEmail()).getItems().isEmpty());
        DirectOrderRequestDTO contactRequest = request(product); contactRequest.setEmail(user.getEmail());
        Order separateAccount = placement.placeDirect(UUID.randomUUID().toString(), contactRequest, checkoutUser.getEmail());
        assertEquals(checkoutUser.getId(), transaction(() -> orders.findById(separateAccount.getId()).orElseThrow().getUser().getId()));
        assertEquals(403, assertThrows(ResponseStatusException.class, () -> service.getOrderById(separateAccount.getId(), user.getEmail())).getStatusCode().value());
    }

    @Test void cancellationRestoresOnlyOneKnownReservationOnPostgresql() throws Exception {
        Product product = product(2); Order order = placement.placeDirect(UUID.randomUUID().toString(), request(product), checkoutUser.getEmail());
        List<Object> results = race(() -> service.updateOrderStatus(order.getId(), "CANCELLED"), () -> service.updateOrderStatus(order.getId(), "CANCELLED"));
        assertTrue(results.stream().allMatch(Order.class::isInstance)); assertEquals(2, products.findById(product.getId()).orElseThrow().getStockQuantity());
        assertFalse(orders.findById(order.getId()).orElseThrow().getInventoryReserved());
    }

    @Test void codEvidenceIsRequiredAndConcurrentCollectionKeepsOneAudit() throws Exception {
        Product product = product(2); Order order = placement.placeDirect(UUID.randomUUID().toString(), request(product), checkoutUser.getEmail());
        assertThrows(ResponseStatusException.class, () -> service.recordCodCollection(order.getId(), new BigDecimal("1400"), "synthetic-settlement", "operator@example.test"));
        service.acceptOrder(order.getId(), "synthetic-confirmation", "operator@example.test");
        service.updateOrderStatus(order.getId(), "SHIPPED"); service.updateOrderStatus(order.getId(), "DELIVERED");
        assertEquals("PENDING_COD", orders.findById(order.getId()).orElseThrow().getPaymentStatus());
        List<Object> results = race(() -> service.recordCodCollection(order.getId(), new BigDecimal("1400"), "synthetic-settlement", "first@example.test"),
                () -> service.recordCodCollection(order.getId(), new BigDecimal("1400"), "synthetic-settlement", "second@example.test"));
        assertTrue(results.stream().allMatch(Order.class::isInstance)); Order collected = orders.findById(order.getId()).orElseThrow();
        assertEquals("PAID", collected.getPaymentStatus()); assertEquals("synthetic-settlement", collected.getCodCollectionReference());
        assertNotNull(collected.getCodCollectedAt()); assertTrue(Set.of("first@example.test", "second@example.test").contains(collected.getCodCollectedBy()));
        assertEquals(1400.0, collected.getTotalAmount()); assertEquals(400.0, collected.getDeliveryCharge());
    }

    @Test void merchandiseCouponNeverReducesShippingAndHistoryStaysUnchanged() {
        Product product = product(10); DirectOrderRequestDTO request = request(product); request.setQuantity(3); request.setCouponCode("424"); request.setExpectedTotal(new BigDecimal("3100"));
        promotions.save(null, new com.cosmetic.ecommerce.dto.PromotionRequest("424", new BigDecimal("10"), true, Set.of(product.getId())));
        Order order = placement.placeDirect(UUID.randomUUID().toString(), request, checkoutUser.getEmail());
        assertEquals(3100.0, order.getTotalAmount()); assertEquals(400.0, order.getDeliveryCharge()); assertEquals(300.0, order.getDiscountAmount());
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

    @Test void passwordResetMigrationPersistsOnlyDigestsAndPreservesAccountData() {
        User customer = user("USER"); String oldHash = customer.getPassword(); String raw = resetToken(customer);
        List<String> columns = jdbc.queryForList("select column_name from information_schema.columns where table_schema='public' and table_name='password_reset_tokens' order by ordinal_position", String.class);
        assertEquals(List.of("token_hash", "user_id", "created_at", "expires_at", "used_at"), columns);
        String stored = jdbc.queryForObject("select token_hash from password_reset_tokens where user_id=?", String.class, customer.getId());
        assertEquals(PasswordResetService.hash(raw), stored); assertNotEquals(raw, stored); assertTrue(stored.matches("[0-9a-f]{64}"));
        assertEquals(20 * 60, jdbc.queryForObject("select extract(epoch from (expires_at-created_at))::integer from password_reset_tokens where user_id=?", Integer.class, customer.getId()));
        assertEquals(2, jdbc.queryForObject("select count(*) from pg_indexes where schemaname='public' and tablename='password_reset_tokens' and indexname in ('idx_password_reset_user','idx_password_reset_expiry')", Integer.class));
        User unchanged = users.findById(customer.getId()).orElseThrow();
        assertEquals(oldHash, unchanged.getPassword()); assertEquals(0, unchanged.getSessionVersion()); assertTrue(unchanged.isEnabled());
    }

    @Test void successfulPasswordResetRevokesActualJwtAndReplacesActualLoginPassword() throws Exception {
        User customer = user("USER"); String oldBearer = bearer(customer), raw = resetToken(customer);
        String replacement = "replacement-synthetic-password";
        mvc.perform(get("/api/orders").header("Authorization", oldBearer)).andExpect(status().isOk());
        passwordResets.reset(raw, replacement, replacement);
        User current = users.findById(customer.getId()).orElseThrow();
        assertEquals(1, current.getSessionVersion()); assertTrue(passwords.matches(replacement, current.getPassword()));
        assertFalse(passwords.matches("synthetic-valid-test-password", current.getPassword()));
        assertEquals(1, jdbc.queryForObject("select count(*) from password_reset_tokens where user_id=? and used_at is not null", Integer.class, customer.getId()));
        mvc.perform(get("/api/orders").header("Authorization", oldBearer)).andExpect(status().isUnauthorized());
        mvc.perform(post("/api/auth/login").contentType("application/json").content("{\"email\":\"" + customer.getEmail() + "\",\"password\":\"synthetic-valid-test-password\"}"))
                .andExpect(status().isUnauthorized());
        var login = mvc.perform(post("/api/auth/login").contentType("application/json").content("{\"email\":\"" + customer.getEmail() + "\",\"password\":\"" + replacement + "\"}"))
                .andExpect(status().isOk()).andReturn();
        var matcher = Pattern.compile("\\\"token\\\"\\s*:\\s*\\\"([A-Za-z0-9_.-]+)\\\"").matcher(login.getResponse().getContentAsString());
        assertTrue(matcher.find());
        mvc.perform(get("/api/orders").header("Authorization", "Bearer " + matcher.group(1))).andExpect(status().isOk());
        assertEquals(400, assertThrows(ResponseStatusException.class, () -> passwordResets.reset(raw, "another-synthetic-password", "another-synthetic-password")).getStatusCode().value());
        assertEquals(1, users.findById(customer.getId()).orElseThrow().getSessionVersion());
    }

    @RepeatedTest(3) void concurrentUseOfSamePasswordResetTokenHasExactlyOneWinnerOnPostgresql() throws Exception {
        User customer = user("USER"); String raw = resetToken(customer);
        assertSinglePasswordResetWinner(customer, raw, raw);
    }

    @RepeatedTest(3) void concurrentUseOfDifferentPasswordResetTokensForOneAccountHasExactlyOneWinnerOnPostgresql() throws Exception {
        User customer = user("USER"); String first = resetToken(customer), second = resetToken(customer);
        assertNotEquals(first, second); assertSinglePasswordResetWinner(customer, first, second);
    }

    private void assertSinglePasswordResetWinner(User customer, String firstToken, String secondToken) throws Exception {
        List<Object> results = race(() -> { passwordResets.reset(firstToken, "synthetic-first-password", "synthetic-first-password"); return "first"; },
                () -> { passwordResets.reset(secondToken, "synthetic-second-password", "synthetic-second-password"); return "second"; });
        assertEquals(1, results.stream().filter(String.class::isInstance).count());
        assertEquals(1, results.stream().filter(ResponseStatusException.class::isInstance).count());
        ResponseStatusException rejected = (ResponseStatusException) results.stream().filter(ResponseStatusException.class::isInstance).findFirst().orElseThrow();
        assertEquals(400, rejected.getStatusCode().value()); assertEquals(PasswordResetService.INVALID_LINK, rejected.getReason());
        User current = users.findById(customer.getId()).orElseThrow(); assertEquals(1, current.getSessionVersion());
        String winner = (String) results.stream().filter(String.class::isInstance).findFirst().orElseThrow();
        assertTrue(passwords.matches("first".equals(winner) ? "synthetic-first-password" : "synthetic-second-password", current.getPassword()));
        assertEquals(0, jdbc.queryForObject("select count(*) from password_reset_tokens where user_id=? and used_at is null", Integer.class, customer.getId()));
    }

    @Test void actualSecurityChainAllowsExactCorsAndIdempotencyHeaderOnly() throws Exception {
        mvc.perform(options("/api/orders/direct").header("Origin", "https://isolated.sekkin.test").header("Access-Control-Request-Method", "POST")
                .header("Access-Control-Request-Headers", "content-type,idempotency-key,authorization"))
                .andExpect(status().isOk()).andExpect(header().string("Access-Control-Allow-Origin", "https://isolated.sekkin.test"))
                .andExpect(header().doesNotExist("Access-Control-Allow-Credentials"));
        mvc.perform(options("/api/orders/direct").header("Origin", "https://unapproved.example").header("Access-Control-Request-Method", "POST"))
                .andExpect(status().isForbidden());
    }

    @Test void realLoginIsRequiredForCodHttpCreationWithoutAnyCardCollection() throws Exception {
        User customer = user("USER");
        var login = mvc.perform(post("/api/auth/login").contentType("application/json")
                .content("{\"email\":\"" + customer.getEmail() + "\",\"password\":\"synthetic-valid-test-password\"}"))
                .andExpect(status().isOk()).andReturn();
        var matcher = Pattern.compile("\\\"token\\\"\\s*:\\s*\\\"([A-Za-z0-9_.-]+)\\\"").matcher(login.getResponse().getContentAsString());
        assertTrue(matcher.find()); String token = matcher.group(1); assertTrue(jwt.validateToken(token));
        mvc.perform(get("/api/orders").header("Authorization", "Bearer " + token)).andExpect(status().isOk());
        Product product = product(3); String key = UUID.randomUUID().toString(); long before = orders.count();
        String body = "{\"productId\":" + product.getId() + ",\"quantity\":1,\"name\":\"Synthetic buyer\",\"phoneNumber\":\"test-phone\","
                + "\"address\":\"Synthetic test address\",\"email\":\"guest@example.test\",\"paymentMethod\":\"COD\",\"expectedTotal\":1400.00}";
        mvc.perform(post("/api/orders/direct").header("Idempotency-Key", key).contentType("application/json").content(body))
                .andExpect(status().isUnauthorized());
        assertEquals(before, orders.count()); assertEquals(3, products.findById(product.getId()).orElseThrow().getStockQuantity());
        for (int retry = 0; retry < 2; retry++) mvc.perform(post("/api/orders/direct").header("Authorization", "Bearer " + token).header("Idempotency-Key", key)
                .contentType("application/json").content(body)).andExpect(status().isOk())
                .andExpect(jsonPath("$.deliveryCharge").value(400)).andExpect(jsonPath("$.totalAmount").value(1400))
                .andExpect(jsonPath("$.paymentStatus").value("PENDING_COD"));
        assertEquals(before + 1, orders.count()); assertEquals(2, products.findById(product.getId()).orElseThrow().getStockQuantity());
        mvc.perform(post("/api/orders/direct").header("Authorization", "Bearer " + token).header("Idempotency-Key", UUID.randomUUID().toString()).contentType("application/json")
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
                .andExpect(jsonPath("$.deliveryCharge").value(400))
                .andExpect(jsonPath("$.orderItems").doesNotExist());
        mvc.perform(get("/api/orders/" + order.getId()).header("Authorization", bearer(customer)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.id").value(order.getId()))
                .andExpect(jsonPath("$.user").doesNotExist());
    }
}
