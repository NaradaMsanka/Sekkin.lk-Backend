package com.cosmetic.ecommerce.security;

import com.cosmetic.ecommerce.config.AdminInitializer;
import com.cosmetic.ecommerce.entity.User;
import com.cosmetic.ecommerce.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.security.crypto.password.PasswordEncoder;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class AdminBootstrapSafetyTest {
    @Test
    void normalStartupRegistersNoBootstrapRunnerOrDatabaseDependency() {
        try (var context = new AnnotationConfigApplicationContext()) {
            context.register(AdminInitializer.class);
            context.refresh();
            assertTrue(context.getBeansOfType(CommandLineRunner.class).isEmpty());
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"production", "prod"})
    void productionProfileCannotActivateLocalBootstrap(String profile) {
        try (var context = new AnnotationConfigApplicationContext()) {
            context.getEnvironment().setActiveProfiles(profile, "local-admin-bootstrap");
            context.register(AdminInitializer.class);
            context.refresh();
            assertTrue(context.getBeansOfType(CommandLineRunner.class).isEmpty());
        }
    }

    @Test
    void localBootstrapRequiresExplicitCredentialsBeforeRepositoryAccess() {
        UserRepository users = mock(UserRepository.class);
        PasswordEncoder encoder = mock(PasswordEncoder.class);
        assertThrows(IllegalArgumentException.class, () -> new AdminInitializer().initAdmin(users, encoder, "", ""));
        verifyNoInteractions(users, encoder);
    }

    @Test
    void explicitLocalSetupDoesNotOverwriteAnExistingAccount() throws Exception {
        UserRepository users = mock(UserRepository.class);
        PasswordEncoder encoder = mock(PasswordEncoder.class);
        when(users.existsByEmail("fixture-admin@example.test")).thenReturn(true);
        new AdminInitializer().initAdmin(users, encoder, "fixture-admin@example.test", "test-only-long-bootstrap-password").run();
        verify(users, never()).save(any(User.class));
        verifyNoInteractions(encoder);
    }

    @Test
    void explicitLocalSetupHashesSuppliedCredentialsForANewAdminOnly() throws Exception {
        UserRepository users = mock(UserRepository.class);
        PasswordEncoder encoder = mock(PasswordEncoder.class);
        when(encoder.encode("test-only-long-bootstrap-password")).thenReturn("test-only-encoded-value");
        new AdminInitializer().initAdmin(users, encoder, "fixture-admin@example.test", "test-only-long-bootstrap-password").run();
        verify(users).save(argThat(user -> "ADMIN".equals(user.getRole())
                && "fixture-admin@example.test".equals(user.getEmail())
                && "test-only-encoded-value".equals(user.getPassword())));
    }
}
