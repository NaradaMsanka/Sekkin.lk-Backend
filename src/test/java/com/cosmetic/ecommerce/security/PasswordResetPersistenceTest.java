package com.cosmetic.ecommerce.security;

import com.cosmetic.ecommerce.config.PasswordResetProperties;
import com.cosmetic.ecommerce.entity.*;
import com.cosmetic.ecommerce.repository.*;
import com.cosmetic.ecommerce.service.PasswordResetService;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.*;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.transaction.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import static org.junit.jupiter.api.Assertions.*;

@DataJpaTest(showSql = false, properties = {"spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.jpa.database-platform=org.hibernate.dialect.H2Dialect", "spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.H2Dialect"})
@ContextConfiguration(classes = PasswordResetPersistenceTest.Config.class)
@Import(PasswordResetService.class)
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class PasswordResetPersistenceTest {
    @Configuration @EntityScan(basePackageClasses = User.class) @EnableJpaRepositories(basePackageClasses = UserRepository.class)
    static class Config {
        @Bean PasswordResetProperties settings() { return new PasswordResetProperties(); }
        @Bean PasswordEncoder passwords() { return new BCryptPasswordEncoder(4); }
        @Bean MutableClock clock() { return new MutableClock(); }
    }
    static class MutableClock extends Clock {
        volatile Instant now = Instant.parse("2026-11-22T00:00:00Z");
        public ZoneId getZone() { return ZoneOffset.UTC; }
        public Clock withZone(ZoneId zone) { return this; }
        public Instant instant() { return now; }
    }
    @Autowired UserRepository users;
    @Autowired PasswordResetTokenRepository tokens;
    @Autowired PasswordResetService resets;
    @Autowired PasswordEncoder passwords;
    @Autowired MutableClock clock;
    @BeforeEach void time() { clock.now = Instant.parse("2026-11-22T00:00:00Z"); }
    @AfterEach void clean() { tokens.deleteAll(); users.deleteAll(); }
    private User user(String email) {
        User user = new User(); user.setEmail(email); user.setName("Synthetic recovery account");
        user.setPassword(passwords.encode("old-test-password")); return users.saveAndFlush(user);
    }
    private String raw(PasswordResetService.Delivery delivery) { return delivery.url().substring(delivery.url().indexOf("#token=") + 7); }
    private String issue(User user) { return raw(resets.issue(user.getEmail())); }

    @Test void storesOnlyASecureDigestWithTwentyMinuteExpiry() {
        User user = user("owner@example.test");
        var delivery = resets.issue(user.getEmail()); String raw = raw(delivery);
        assertTrue(raw.matches("[A-Za-z0-9_-]{43}"));
        assertEquals("PasswordResetDelivery[REDACTED]", delivery.toString());
        var persisted = tokens.findById(PasswordResetService.hash(raw)).orElseThrow();
        assertNotEquals(raw, persisted.getTokenHash());
        assertEquals(64, persisted.getTokenHash().length());
        assertEquals(clock.instant().plusSeconds(1200), persisted.getExpiresAt());
        assertNull(persisted.getUsedAt());
    }
    @Test void unknownDisabledAndAmbiguousLegacyAccountsDoNotReceiveTokens() {
        assertNull(resets.issue("unknown@example.test"));
        User disabled = user("disabled@example.test"); disabled.setEnabled(false); users.saveAndFlush(disabled);
        assertNull(resets.issue(disabled.getEmail()));
        user("Mixed@example.test"); user("mixed@example.test");
        assertNull(resets.issue("MIXED@example.test"));
        assertEquals(0, tokens.count());
    }
    @Test void mixedCaseUniqueAccountCanRecoverWithoutChangingStoredIdentity() {
        User user = user("MixedCase@example.test");
        var delivery = resets.issue("mixedcase@example.test");
        assertEquals(user.getEmail(), delivery.email());
        assertEquals(user.getEmail(), users.findById(user.getId()).orElseThrow().getEmail());
    }
    @Test void validResetChangesHashRevokesSessionsAndConsumesEveryOutstandingLink() {
        User user = user("valid@example.test"); String first = issue(user), second = issue(user);
        resets.reset(first, "new-test-password", "new-test-password");
        User changed = users.findById(user.getId()).orElseThrow();
        assertTrue(passwords.matches("new-test-password", changed.getPassword()));
        assertFalse(passwords.matches("old-test-password", changed.getPassword()));
        assertEquals(1, changed.getSessionVersion());
        assertTrue(tokens.findAll().stream().allMatch(token -> token.getUsedAt() != null));
        assertThrows(ResponseStatusException.class, () -> resets.reset(first, "another-password", "another-password"));
        assertThrows(ResponseStatusException.class, () -> resets.reset(second, "another-password", "another-password"));
    }
    @Test void invalidAndExactlyExpiredTokensLeavePasswordAndSessionUntouched() {
        User user = user("expired@example.test"); String raw = issue(user);
        assertThrows(ResponseStatusException.class, () -> resets.reset("x".repeat(43), "new-test-password", "new-test-password"));
        clock.now = clock.now.plusSeconds(1200);
        assertThrows(ResponseStatusException.class, () -> resets.reset(raw, "new-test-password", "new-test-password"));
        assertEquals(0, users.findById(user.getId()).orElseThrow().getSessionVersion());
        assertNull(tokens.findById(PasswordResetService.hash(raw)).orElseThrow().getUsedAt());
    }
    @Test void mismatchedInvalidUnicodeAndOversizePasswordsDoNotConsumeTheLink() {
        User user = user("policy@example.test"); String raw = issue(user);
        assertThrows(ResponseStatusException.class, () -> resets.reset(raw, "new-test-password", "different"));
        for (String value : new String[]{"short", " ".repeat(6), "a".repeat(73), "\u00e9".repeat(37), "\uD800abcdef"})
            assertThrows(ResponseStatusException.class, () -> resets.reset(raw, value, value));
        assertNull(tokens.findById(PasswordResetService.hash(raw)).orElseThrow().getUsedAt());
        resets.reset(raw, "a".repeat(72), "a".repeat(72));
    }
    @Test void disabledAccountAndExhaustedSessionVersionCannotReset() {
        User user = user("blocked@example.test"); String raw = issue(user);
        user.setEnabled(false); users.saveAndFlush(user);
        assertThrows(ResponseStatusException.class, () -> resets.reset(raw, "new-test-password", "new-test-password"));
        user.setEnabled(true); user.setSessionVersion(Long.MAX_VALUE); users.saveAndFlush(user);
        assertThrows(ResponseStatusException.class, () -> resets.reset(raw, "new-test-password", "new-test-password"));
    }
    @Test void concurrentReuseOfOneTokenHasExactlyOneWinner() throws Exception { race(false); }
    @Test void concurrentDifferentTokensForOneAccountHaveExactlyOneWinner() throws Exception { race(true); }
    private void race(boolean differentTokens) throws Exception {
        User user = user("race@example.test"); String first = issue(user), second = differentTokens ? issue(user) : first;
        CountDownLatch ready = new CountDownLatch(2), start = new CountDownLatch(1);
        try (ExecutorService pool = Executors.newFixedThreadPool(2)) {
            List<Future<Boolean>> outcomes = new ArrayList<>();
            for (String raw : List.of(first, second)) outcomes.add(pool.submit(() -> {
                ready.countDown(); assertTrue(start.await(10, TimeUnit.SECONDS));
                try { resets.reset(raw, "new-test-password", "new-test-password"); return true; }
                catch (ResponseStatusException used) { assertEquals(400, used.getStatusCode().value()); return false; }
            }));
            assertTrue(ready.await(10, TimeUnit.SECONDS)); start.countDown();
            assertEquals(1, (outcomes.get(0).get(15, TimeUnit.SECONDS) ? 1 : 0) + (outcomes.get(1).get(15, TimeUnit.SECONDS) ? 1 : 0));
        }
        assertEquals(1, users.findById(user.getId()).orElseThrow().getSessionVersion());
    }
}
