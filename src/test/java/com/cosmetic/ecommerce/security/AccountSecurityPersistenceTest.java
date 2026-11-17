package com.cosmetic.ecommerce.security;

import com.cosmetic.ecommerce.entity.User;
import com.cosmetic.ecommerce.repository.UserRepository;
import com.cosmetic.ecommerce.service.AccountSecurityService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import java.util.concurrent.*;
import static org.junit.jupiter.api.Assertions.*;

// Embedded H2 only; no full Boot application, server, admin bootstrap or hosted database.
@DataJpaTest(showSql = false, properties = {"spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.jpa.database-platform=org.hibernate.dialect.H2Dialect",
        "spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.H2Dialect"})
@ContextConfiguration(classes = AccountSecurityPersistenceTest.PersistenceConfiguration.class)
@Import(AccountSecurityService.class)
class AccountSecurityPersistenceTest {
    @Configuration @EnableJpaRepositories(basePackageClasses = UserRepository.class) @EntityScan(basePackageClasses = User.class)
    static class PersistenceConfiguration {}
    @Autowired UserRepository users;
    @Autowired AccountSecurityService security;

    private User account(String email) {
        User user = new User(); user.setEmail(email); user.setName("Synthetic account");
        return users.saveAndFlush(user);
    }

    @Test void newAccountsStartEnabledAndRevocationPersistsAnIncrement() {
        User user = account("revoke@example.test"); assertTrue(user.isEnabled()); assertEquals(0, user.getSessionVersion());
        security.revokeCurrentSessions(user.getEmail());
        assertEquals(1, users.findById(user.getId()).orElseThrow().getSessionVersion());
        security.revokeSessionsForUser(user.getId());
        assertEquals(2, users.findById(user.getId()).orElseThrow().getSessionVersion());
    }

    @Test void disablingAndReenablingBothRevokePreviouslyIssuedTokens() {
        User user = account("disable@example.test");
        security.setAccountEnabled(user.getId(), false, "operator@example.test");
        User disabled = users.findById(user.getId()).orElseThrow(); assertFalse(disabled.isEnabled()); assertEquals(1, disabled.getSessionVersion());
        security.setAccountEnabled(user.getId(), true, "operator@example.test");
        User enabled = users.findById(user.getId()).orElseThrow(); assertTrue(enabled.isEnabled()); assertEquals(2, enabled.getSessionVersion());
    }

    @Test void cannotDisableTheAuthenticatedOperator() {
        User user = account("operator@example.test");
        assertThrows(IllegalArgumentException.class, () -> security.setAccountEnabled(user.getId(), false, user.getEmail()));
        assertTrue(users.findById(user.getId()).orElseThrow().isEnabled());
    }

    @Test void absentAccountsAndDisabledCurrentSessionsFailClosed() {
        assertThrows(IllegalArgumentException.class, () -> security.revokeSessionsForUser(-1L));
        assertThrows(IllegalArgumentException.class, () -> security.revokeCurrentSessions("absent@example.test"));
    }

    @Test @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void concurrentRevocationsCannotLoseAnIncrement() throws Exception {
        User user = account("concurrent@example.test");
        try {
            CountDownLatch start = new CountDownLatch(1);
            try (ExecutorService executor = Executors.newFixedThreadPool(2)) {
                Future<?> first = executor.submit(() -> { await(start); security.revokeSessionsForUser(user.getId()); });
                Future<?> second = executor.submit(() -> { await(start); security.revokeSessionsForUser(user.getId()); });
                start.countDown(); first.get(10, TimeUnit.SECONDS); second.get(10, TimeUnit.SECONDS);
            }
            assertEquals(2, users.findById(user.getId()).orElseThrow().getSessionVersion());
        } finally { users.deleteById(user.getId()); }
    }

    private void await(CountDownLatch latch) {
        try { latch.await(); } catch (InterruptedException error) { Thread.currentThread().interrupt(); throw new IllegalStateException(error); }
    }
}
