package com.cosmetic.ecommerce.security;

import com.cosmetic.ecommerce.CosmeticEcommerceBackendApplication;
import com.cosmetic.ecommerce.config.PasswordResetProperties;
import com.cosmetic.ecommerce.controller.PasswordResetController;
import com.cosmetic.ecommerce.entity.User;
import com.cosmetic.ecommerce.repository.*;
import com.cosmetic.ecommerce.service.PasswordResetService;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.mail.Session;
import jakarta.mail.internet.MimeMessage;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.*;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.*;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(classes = CosmeticEcommerceBackendApplication.class, properties = {
        "spring.datasource.url=jdbc:h2:mem:password_recovery_http;MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
        "spring.datasource.username=sa", "spring.datasource.password=", "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.jpa.hibernate.ddl-auto=create-drop", "spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.H2Dialect",
        "spring.jpa.database-platform=org.hibernate.dialect.H2Dialect", "spring.jpa.show-sql=false", "debug=false",
        "security.jwt.secret=public-synthetic-password-recovery-signing-key-0000",
        "app.password-reset.enabled=true", "spring.mail.password=public-synthetic-test-only-mail-password",
        "spring.mail.host=localhost", "app.cors.allowed-origins=https://www.sekkin.lk" })
@AutoConfigureMockMvc
@ActiveProfiles("isolated-password-reset")
class PasswordResetHttpTest {
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired UserRepository users;
    @Autowired PasswordResetTokenRepository tokens;
    @Autowired PasswordResetService resets;
    @Autowired PasswordEncoder passwords;
    @Autowired PasswordResetProperties settings;
    @Autowired @Qualifier("passwordResetExecutor") ThreadPoolTaskExecutor executor;
    @MockBean JavaMailSender mail;
    private static final AtomicInteger IP = new AtomicInteger(20);
    @BeforeEach void prepare() {
        settings.setEnabled(true);
        when(mail.createMimeMessage()).thenAnswer(invocation -> new MimeMessage(Session.getInstance(new Properties())));
    }
    @AfterEach void clean() {
        await().atMost(Duration.ofSeconds(10)).until(() -> executor.getActiveCount() == 0 && executor.getThreadPoolExecutor().getQueue().isEmpty());
        tokens.deleteAll(); users.deleteAll();
    }
    private org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder request(String path, Object body) throws Exception {
        String address = "192.0.2." + IP.incrementAndGet();
        return post(path).contentType("application/json").content(json.writeValueAsString(body))
                .with(req -> { req.setRemoteAddr(address); return req; });
    }
    private User user() {
        User user = new User(); user.setEmail(UUID.randomUUID() + "@example.test"); user.setName("Synthetic HTTP recovery account");
        user.setPassword(passwords.encode("old-test-password")); return users.saveAndFlush(user);
    }
    private String issue(User user) { return resets.issue(user.getEmail()).url().split("#token=", 2)[1]; }
    private Map<String,String> reset(String token, String password, String confirm) { return Map.of("token", token, "newPassword", password, "confirmPassword", confirm); }
    private String login(User user, String password) throws Exception {
        var result = mvc.perform(request("/api/auth/login", Map.of("email", user.getEmail(), "password", password))).andExpect(status().isOk()).andReturn();
        return json.readTree(result.getResponse().getContentAsString()).get("token").asText();
    }

    @Test void publicKnownAndUnknownRequestsHaveTheSameGenericResponse() throws Exception {
        User user = user();
        for (String email : List.of(user.getEmail(), "unknown-" + UUID.randomUUID() + "@example.test")) {
            mvc.perform(request("/api/auth/forgot-password", Map.of("email", email)))
                    .andExpect(status().isOk()).andExpect(jsonPath("$.message").value(PasswordResetController.CONFIRMATION))
                    .andExpect(jsonPath("$.token").doesNotExist()).andExpect(header().string("Cache-Control", "no-store"))
                    .andExpect(header().string("Referrer-Policy", "no-referrer"));
        }
        verify(mail, timeout(5000)).send(any(MimeMessage.class));
    }
    @Test void newPasswordLoginWorksOldPasswordAndOldJwtAreRejected() throws Exception {
        User user = user(); String oldJwt = login(user, "old-test-password"), raw = issue(user);
        mvc.perform(request("/api/auth/reset-password", reset(raw, "new-test-password", "new-test-password")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.token").doesNotExist());
        mvc.perform(request("/api/auth/login", Map.of("email", user.getEmail(), "password", "old-test-password"))).andExpect(status().isUnauthorized());
        mvc.perform(post("/api/auth/logout-all").header("Authorization", "Bearer " + oldJwt)).andExpect(status().isUnauthorized());
        String newJwt = login(user, "new-test-password");
        mvc.perform(post("/api/auth/logout-all").header("Authorization", "Bearer " + newJwt)).andExpect(status().isNoContent());
        verify(mail, timeout(5000)).send(any(MimeMessage.class));
    }
    @Test void invalidLinkAndConfirmationMismatchAreSafeAndDoNotConsumeAValidLink() throws Exception {
        User user = user(); String raw = issue(user);
        mvc.perform(request("/api/auth/reset-password", reset("x".repeat(43), "new-test-password", "new-test-password")))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.message").value(PasswordResetService.INVALID_LINK));
        mvc.perform(request("/api/auth/reset-password", reset(raw, "new-test-password", "mismatch-password")))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.message").value("Passwords do not match."));
        mvc.perform(request("/api/auth/reset-password", reset(raw, "new-test-password", "new-test-password"))).andExpect(status().isOk());
        mvc.perform(request("/api/auth/reset-password", reset(raw, "new-test-password", "new-test-password"))).andExpect(status().isBadRequest());
    }
    @Test void malformedAndOversizeCredentialsAreRejectedWithoutEchoingValues() throws Exception {
        String sensitive = "sensitive-input-".repeat(10);
        var result = mvc.perform(request("/api/auth/reset-password", reset("x".repeat(43), sensitive, sensitive)))
                .andExpect(status().isBadRequest()).andReturn();
        assertFalse(result.getResponse().getContentAsString().contains(sensitive));
        mvc.perform(request("/api/auth/forgot-password", Map.of("email", "invalid-address"))).andExpect(status().isBadRequest());
    }
    @Test void disabledDeliveryUsesTheSame503ForAllAccounts() throws Exception {
        User user = user(); settings.setEnabled(false);
        for (String email : List.of(user.getEmail(), "unknown@example.test"))
            mvc.perform(request("/api/auth/forgot-password", Map.of("email", email))).andExpect(status().isServiceUnavailable())
                    .andExpect(jsonPath("$.message").value("Password recovery is temporarily unavailable. Please try again later."));
        verifyNoInteractions(mail);
    }
    @Test void registrationAndExistingLoginStillWorkWithoutEscalatingRoles() throws Exception {
        String email = UUID.randomUUID() + "@example.test";
        mvc.perform(request("/api/auth/register", Map.of("name", "Synthetic new customer", "email", email, "password", "register-test-password", "role", "ADMIN")))
                .andExpect(status().isOk());
        User user = users.findByEmail(email);
        assertEquals("USER", user.getRole()); assertTrue(passwords.matches("register-test-password", user.getPassword()));
        assertNotNull(login(user, "register-test-password"));
        mvc.perform(request("/api/auth/register", Map.of("name", "Duplicate", "email", email, "password", "register-test-password")))
                .andExpect(status().isBadRequest());
    }
    @Test void endpointsArePublicButAdminAuthorizationRemainsRequired() throws Exception {
        mvc.perform(get("/api/admin/dashboard")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/auth/reset-password")).andExpect(status().isUnauthorized());
        mvc.perform(options("/api/auth/reset-password").header("Origin", "https://www.sekkin.lk")
                        .header("Access-Control-Request-Method", "POST").header("Access-Control-Request-Headers", "Content-Type"))
                .andExpect(status().isOk());
    }
}
