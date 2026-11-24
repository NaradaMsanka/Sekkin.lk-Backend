package com.cosmetic.ecommerce.security;

import com.cosmetic.ecommerce.config.*;
import com.cosmetic.ecommerce.controller.*;
import com.cosmetic.ecommerce.entity.User;
import com.cosmetic.ecommerce.repository.UserRepository;
import com.cosmetic.ecommerce.service.*;
import com.cosmetic.ecommerce.util.JwtUtil;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.any;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(controllers = {AdminController.class, AuthController.class, OrderController.class,
        ProductController.class, CategoryController.class, FileUploadController.class},
        properties = "security.jwt.secret=synthetic-authorization-test-key-0000000000")
@Import({SecurityConfig.class, JwtFilter.class, JwtUtil.class, RequestValidationAdvice.class})
@ExtendWith(OutputCaptureExtension.class)
class AuthorizationBoundaryTest {
    @Autowired MockMvc mvc;
    @Autowired JwtUtil jwt;
    @Autowired org.springframework.security.crypto.password.PasswordEncoder passwords;
    @MockBean private com.cosmetic.ecommerce.service.OrderPlacementService placement;
    @MockBean UserRepository users;
    @MockBean OrderService orders;
    @MockBean AdminService admins;
    @MockBean ProductService products;
    @MockBean CategoryService categories;
    @MockBean CloudinaryService images;
    @MockBean UserService registrations;
    @MockBean AccountSecurityService accountSecurity;
    @MockBean RequestThrottle throttle;

    private User user(String role) {
        User user = new User(); user.setEmail("auth-fixture@example.test"); user.setRole(role); return user;
    }
    private String bearer(String role) { return "Bearer " + jwt.generateToken("auth-fixture@example.test", role); }

    private String directCheckoutBody() {
        return """
                {"productId":1,"quantity":1,"name":"Synthetic buyer","phoneNumber":"0000000000",
                "address":"Synthetic address","email":"other@example.test","paymentMethod":"COD","expectedTotal":1400}
                """;
    }

    @Test void everyCheckoutRouteRejectsAnonymousRequestsBeforePlacement() throws Exception {
        for (String route : new String[]{"/api/orders", "/api/orders/direct"}) {
            mvc.perform(post(route).header("Idempotency-Key", java.util.UUID.randomUUID().toString())
                            .contentType("application/json").content(directCheckoutBody()))
                    .andExpect(status().isUnauthorized());
        }
        verifyNoInteractions(placement, orders, users);
    }

    @Test void invalidExpiredDisabledAndRevokedSessionsCannotPlaceDirectOrders() throws Exception {
        String expired = Jwts.builder().setSubject("auth-fixture@example.test").claim("sv", 0)
                .setExpiration(new Date(System.currentTimeMillis() - 60000))
                .signWith(Keys.hmacShaKeyFor("synthetic-authorization-test-key-0000000000".getBytes(StandardCharsets.UTF_8)), SignatureAlgorithm.HS256).compact();
        String forged = new JwtUtil("different-synthetic-test-key-000000000000").generateToken("auth-fixture@example.test", "USER");
        for (String token : new String[]{"bad-token", expired, forged}) {
            mvc.perform(post("/api/orders/direct").header("Authorization", "Bearer " + token)
                            .header("Idempotency-Key", java.util.UUID.randomUUID().toString()).contentType("application/json").content(directCheckoutBody()))
                    .andExpect(status().isUnauthorized());
        }
        mvc.perform(post("/api/orders/direct").header("Authorization", bearer("USER"))
                        .header("Idempotency-Key", java.util.UUID.randomUUID().toString()).contentType("application/json").content(directCheckoutBody()))
                .andExpect(status().isUnauthorized());
        User account = user("USER"); account.setEnabled(false); when(users.findByEmail(account.getEmail())).thenReturn(account);
        String token = bearer("USER");
        mvc.perform(post("/api/orders/direct").header("Authorization", token)
                        .header("Idempotency-Key", java.util.UUID.randomUUID().toString()).contentType("application/json").content(directCheckoutBody()))
                .andExpect(status().isUnauthorized());
        account.setEnabled(true); account.setSessionVersion(1);
        mvc.perform(post("/api/orders/direct").header("Authorization", token)
                        .header("Idempotency-Key", java.util.UUID.randomUUID().toString()).contentType("application/json").content(directCheckoutBody()))
                .andExpect(status().isUnauthorized());
        verifyNoInteractions(placement, orders);
    }

    @Test void signedInDirectCheckoutUsesThePrincipalInsteadOfTheContactEmail() throws Exception {
        User account = user("USER"); when(users.findByEmail(account.getEmail())).thenReturn(account);
        var product = new com.cosmetic.ecommerce.entity.Product(); product.setName("Synthetic product");
        var item = new com.cosmetic.ecommerce.entity.OrderItem(); item.setProduct(product); item.setQuantity(1); item.setPrice(1000.0);
        var order = new com.cosmetic.ecommerce.entity.Order(); order.setId(1L); order.setUser(account);
        order.setDeliveryCharge(400.0); order.setTotalAmount(1400.0); order.setDiscountAmount(0.0);
        order.setPaymentMethod("COD"); order.setPaymentStatus("PENDING_COD"); order.setStatus("PENDING"); order.getOrderItems().add(item);
        when(placement.placeDirect(any(), any(), eq(account.getEmail()))).thenReturn(order);
        String key = java.util.UUID.randomUUID().toString();
        mvc.perform(post("/api/orders/direct").header("Authorization", bearer("USER")).header("Idempotency-Key", key)
                        .contentType("application/json").content(directCheckoutBody()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.orderId").value(1)).andExpect(jsonPath("$.totalAmount").value(1400));
        verify(placement).placeDirect(eq(key), any(), eq("auth-fixture@example.test"));
    }

    @Test void signedTokenRoleCannotOverrideTheCurrentAccountRole() throws Exception {
        when(users.findByEmail("auth-fixture@example.test")).thenReturn(user("USER"));
        mvc.perform(get("/api/admin/dashboard").header("Authorization", bearer("ADMIN"))).andExpect(status().isForbidden());
        verifyNoInteractions(admins);
    }

    @Test void adminDemotionTakesEffectOnNextRequest() throws Exception {
        User account = user("ADMIN"); when(users.findByEmail(account.getEmail())).thenReturn(account);
        String token = bearer("ADMIN");
        mvc.perform(get("/api/admin/dashboard").header("Authorization", token)).andExpect(status().isOk());
        account.setRole("USER");
        mvc.perform(get("/api/admin/dashboard").header("Authorization", token)).andExpect(status().isForbidden());
        verify(admins, times(1)).getDashboardSummary();
    }

    @Test void missingOrUnsupportedAccountsCannotAuthenticate() throws Exception {
        mvc.perform(get("/api/orders").header("Authorization", bearer("USER"))).andExpect(status().isUnauthorized());
        when(users.findByEmail("auth-fixture@example.test")).thenReturn(user("ROLE_ADMIN"));
        mvc.perform(get("/api/orders").header("Authorization", bearer("ADMIN"))).andExpect(status().isUnauthorized());
        verifyNoInteractions(orders);
    }

    @Test void disabledAccountsAndRevokedSessionVersionsCannotAuthenticate() throws Exception {
        User account = user("USER"); when(users.findByEmail(account.getEmail())).thenReturn(account);
        String token = bearer("USER");
        account.setEnabled(false);
        mvc.perform(get("/api/orders").header("Authorization", token)).andExpect(status().isUnauthorized());
        account.setEnabled(true); account.setSessionVersion(1);
        mvc.perform(get("/api/orders").header("Authorization", token)).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/orders").header("Authorization", "Bearer " + jwt.generateToken(account.getEmail(), "USER", 1)))
                .andExpect(status().isOk());
        verify(orders, times(1)).getUserOrders(any());
    }

    @Test void legacyTokensWithoutSessionVersionAreRejectedBeforeAccountLookup() throws Exception {
        String legacy = Jwts.builder().setSubject("auth-fixture@example.test")
                .setExpiration(new Date(System.currentTimeMillis() + 60000))
                .signWith(Keys.hmacShaKeyFor("synthetic-authorization-test-key-0000000000".getBytes(StandardCharsets.UTF_8)), SignatureAlgorithm.HS256).compact();
        mvc.perform(get("/api/orders").header("Authorization", "Bearer " + legacy)).andExpect(status().isUnauthorized());
        verifyNoInteractions(users, orders);
    }

    @Test void logoutAllRequiresAuthenticationAndUsesTheAuthenticatedIdentity() throws Exception {
        mvc.perform(post("/api/auth/logout-all")).andExpect(status().isUnauthorized());
        User account = user("USER"); when(users.findByEmail(account.getEmail())).thenReturn(account);
        mvc.perform(post("/api/auth/logout-all").header("Authorization", bearer("USER")))
                .andExpect(status().isNoContent());
        verify(accountSecurity).revokeCurrentSessions("auth-fixture@example.test");
    }

    @Test void disabledAccountCannotObtainANewToken() throws Exception {
        User account = user("USER"); account.setEnabled(false); account.setPassword(passwords.encode("synthetic-login-password"));
        when(users.findByEmail(account.getEmail())).thenReturn(account);
        mvc.perform(post("/api/auth/login").contentType("application/json")
                        .content("{\"email\":\"auth-fixture@example.test\",\"password\":\"synthetic-login-password\"}"))
                .andExpect(status().isUnauthorized()).andExpect(content().string("Invalid email or password"));
    }

    @Test void concurrentAccountDisableDuringLogoutIsAGenericConflict() throws Exception {
        User account = user("USER"); when(users.findByEmail(account.getEmail())).thenReturn(account);
        doThrow(new IllegalArgumentException("synthetic-sensitive-account-detail")).when(accountSecurity).revokeCurrentSessions(account.getEmail());
        mvc.perform(post("/api/auth/logout-all").header("Authorization", bearer("USER")))
                .andExpect(status().isConflict()).andExpect(content().string(""));
    }

    @Test void loginThrottleStopsBeforeAccountLookup() throws Exception {
        when(throttle.attempt(any(), any(), anyInt(), any())).thenReturn(42L);
        mvc.perform(post("/api/auth/login").contentType("application/json")
                        .content("{\"email\":\"auth-fixture@example.test\",\"password\":\"synthetic-login-password\"}"))
                .andExpect(status().isTooManyRequests()).andExpect(header().string("Retry-After", "42"));
        verifyNoInteractions(users);
    }

    @Test void malformedExpiredAndForgedTokensNeverReachAccountLookup() throws Exception {
        String expired = Jwts.builder().setSubject("auth-fixture@example.test")
                .setExpiration(new Date(System.currentTimeMillis() - 60000))
                .signWith(Keys.hmacShaKeyFor("synthetic-authorization-test-key-0000000000".getBytes(StandardCharsets.UTF_8)), SignatureAlgorithm.HS256).compact();
        String forged = new JwtUtil("different-synthetic-test-key-000000000000").generateToken("auth-fixture@example.test", "ADMIN");
        for (String token : new String[]{"bad-token", expired, forged}) {
            mvc.perform(get("/api/orders").header("Authorization", "Bearer " + token)).andExpect(status().isUnauthorized());
        }
        verifyNoInteractions(users, orders);
    }

    @Test void accountLookupFailureFailsClosedWithoutLoggingDatabaseDetails(CapturedOutput output) throws Exception {
        when(users.findByEmail("auth-fixture@example.test")).thenThrow(new RuntimeException("synthetic-sensitive-db-detail"));
        mvc.perform(get("/api/orders").header("Authorization", bearer("USER"))).andExpect(status().isServiceUnavailable())
                .andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("sensitive-db"))));
        assertFalse(output.getAll().contains("synthetic-sensitive-db-detail")); verifyNoInteractions(orders);
    }

    @Test void anonymousAndCustomersCannotReachAdminOrWriteProductsOrChangeStatus() throws Exception {
        mvc.perform(get("/api/admin/users")).andExpect(status().isUnauthorized());
        when(users.findByEmail("auth-fixture@example.test")).thenReturn(user("USER"));
        String token = bearer("USER");
        mvc.perform(get("/api/admin/users").header("Authorization", token)).andExpect(status().isForbidden());
        mvc.perform(delete("/api/products/1").header("Authorization", token)).andExpect(status().isForbidden());
        mvc.perform(patch("/api/orders/1/status").param("status", "PAID").header("Authorization", token)).andExpect(status().isForbidden());
        verifyNoInteractions(admins, products, orders);
    }

    @Test void operationalAndPerAccountAdminRoutesRejectAnonymousAndCustomerRequests() throws Exception {
        for (boolean authenticated : new boolean[]{false, true}) {
            if (authenticated) when(users.findByEmail("auth-fixture@example.test")).thenReturn(user("USER"));
            var requests = java.util.List.of(
                    get("/api/admin/orders"),
                    post("/api/admin/orders/1/accept").contentType("application/json").content("{\"reference\":\"synthetic-confirmation\"}"),
                    post("/api/admin/orders/1/cod-collection").contentType("application/json").content("{\"amount\":1350,\"reference\":\"synthetic-collection\"}"),
                    post("/api/admin/users/2/revoke-sessions"),
                    patch("/api/admin/users/2/enabled").contentType("application/json").content("{\"enabled\":false}"));
            for (var request : requests) {
                if (authenticated) request.header("Authorization", bearer("USER"));
                mvc.perform(request).andExpect(authenticated ? status().isForbidden() : status().isUnauthorized());
            }
        }
        verifyNoInteractions(admins, orders, accountSecurity);
    }

    @Test void adminOperationalRoutesUseTheAuthenticatedOperator() throws Exception {
        when(users.findByEmail("auth-fixture@example.test")).thenReturn(user("ADMIN"));
        String token = bearer("ADMIN"); when(admins.getAllOrders()).thenReturn(java.util.List.of());
        mvc.perform(get("/api/admin/orders").header("Authorization", token)).andExpect(status().isOk()).andExpect(content().json("[]"));
        mvc.perform(post("/api/admin/orders/1/accept").header("Authorization", token).contentType("application/json")
                .content("{\"reference\":\"synthetic-confirmation\",\"operatorEmail\":\"untrusted@example.test\"}"))
                .andExpect(status().isOk());
        mvc.perform(post("/api/admin/orders/1/cod-collection").header("Authorization", token).contentType("application/json")
                .content("{\"amount\":1350.00,\"reference\":\"synthetic-collection\",\"operatorEmail\":\"untrusted@example.test\"}"))
                .andExpect(status().isOk());
        mvc.perform(post("/api/admin/users/2/revoke-sessions").header("Authorization", token)).andExpect(status().isNoContent());
        mvc.perform(patch("/api/admin/users/2/enabled").header("Authorization", token).contentType("application/json")
                .content("{\"enabled\":false,\"operatorEmail\":\"untrusted@example.test\"}"))
                .andExpect(status().isNoContent());
        verify(orders).acceptOrder(1L, "synthetic-confirmation", "auth-fixture@example.test");
        verify(orders).recordCodCollection(1L, new java.math.BigDecimal("1350.00"), "synthetic-collection", "auth-fixture@example.test");
        verify(accountSecurity).revokeSessionsForUser(2L);
        verify(accountSecurity).setAccountEnabled(2L, false, "auth-fixture@example.test");
    }

    @Test void expectedAccountOperationDenialsReturnGenericConflict(CapturedOutput output) throws Exception {
        when(users.findByEmail("auth-fixture@example.test")).thenReturn(user("ADMIN"));
        doThrow(new IllegalArgumentException("synthetic-sensitive-account-operation")).when(accountSecurity).revokeSessionsForUser(99L);
        doThrow(new IllegalArgumentException("synthetic-sensitive-account-operation")).when(accountSecurity).setAccountEnabled(1L, false, "auth-fixture@example.test");
        String token = bearer("ADMIN");
        mvc.perform(post("/api/admin/users/99/revoke-sessions").header("Authorization", token))
                .andExpect(status().isConflict()).andExpect(content().string(""));
        mvc.perform(patch("/api/admin/users/1/enabled").header("Authorization", token).contentType("application/json").content("{\"enabled\":false}"))
                .andExpect(status().isConflict()).andExpect(content().string(""));
        assertFalse(output.getAll().contains("synthetic-sensitive-account-operation"));
    }

    @Test void extremeAuthenticatedExpectedTotalIsRejectedBeforePlacement() throws Exception {
        when(users.findByEmail("auth-fixture@example.test")).thenReturn(user("USER"));
        for (String amount : new String[]{"1e1000000", "1e-1000000"}) {
            mvc.perform(post("/api/orders/direct").header("Authorization", bearer("USER")).header("Idempotency-Key", java.util.UUID.randomUUID().toString())
                    .contentType("application/json").content("{\"productId\":1,\"quantity\":1,\"name\":\"Synthetic\",\"phoneNumber\":\"000\","
                            + "\"address\":\"Synthetic address\",\"email\":\"fixture@example.test\",\"paymentMethod\":\"COD\",\"expectedTotal\":" + amount + "}"))
                    .andExpect(status().isBadRequest());
        }
        verifyNoInteractions(placement, orders);
    }

    @Test void explicitCorsOriginPassesButOtherTenantsDoNot() throws Exception {
        mvc.perform(options("/api/orders").header("Origin", "https://sekkin-lk-frontend.vercel.app")
                .header("Access-Control-Request-Method", "POST").header("Access-Control-Request-Headers", "authorization,content-type"))
                .andExpect(status().isOk()).andExpect(header().string("Access-Control-Allow-Origin", "https://sekkin-lk-frontend.vercel.app"))
                .andExpect(header().doesNotExist("Access-Control-Allow-Credentials"));
        for (String origin : new String[]{"https://unrelated.vercel.app", "https://unrelated.onrender.com", "https://unrelated.netlify.app"}) {
            mvc.perform(options("/api/orders").header("Origin", origin).header("Access-Control-Request-Method", "POST"))
                    .andExpect(status().isForbidden()).andExpect(header().doesNotExist("Access-Control-Allow-Origin"));
        }
    }

    @Test void credentialValidationReturnsGenericErrorsAndDtosDoNotLogPasswords(CapturedOutput output) throws Exception {
        mvc.perform(post("/api/auth/login").contentType("application/json").content("{\"email\":\"sensitive-invalid-email\",\"password\":\"synthetic-sensitive-password\"}"))
                .andExpect(status().isBadRequest()).andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("synthetic-sensitive-password"))));
        var dto = new com.cosmetic.ecommerce.dto.UserLoginDto(); dto.setPassword("synthetic-sensitive-password");
        assertFalse(dto.toString().contains("synthetic-sensitive-password"));
        assertFalse(output.getAll().contains("synthetic-sensitive-password"));
        assertFalse(output.getAll().contains("sensitive-invalid-email"));
    }

    @Test void overlongPasswordsAreRejectedBeforeLoginLookupOrRegistration(CapturedOutput output) throws Exception {
        String prefix = "x".repeat(72);
        String submitted = prefix + "synthetic-sensitive-overlong-suffix";
        String escaped = new com.fasterxml.jackson.databind.json.JsonMapper().writeValueAsString(submitted);
        mvc.perform(post("/api/auth/login").contentType("application/json")
                .content("{\"email\":\"fixture@example.test\",\"password\":" + escaped + "}"))
                .andExpect(status().isBadRequest()).andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString(submitted))));
        mvc.perform(post("/api/auth/register").contentType("application/json")
                .content("{\"name\":\"Synthetic\",\"email\":\"fixture@example.test\",\"password\":" + escaped + "}"))
                .andExpect(status().isBadRequest()).andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString(submitted))));
        verifyNoInteractions(users, registrations);
        assertFalse(output.getAll().contains(submitted));
    }

    @Test void realJwtAuthenticationDoesNotLogTokenOrCustomerIdentity(CapturedOutput output) throws Exception {
        when(users.findByEmail("auth-fixture@example.test")).thenReturn(user("USER"));
        String token = bearer("USER");
        mvc.perform(get("/api/orders").header("Authorization", token)).andExpect(status().isOk());
        assertFalse(output.getAll().contains(token));
        assertFalse(output.getAll().contains("authenticated user=auth-fixture@example.test"));
    }

    @Test void internalErrorDispatchRetainsFailureStatusWithoutExposingDetails() throws Exception {
        mvc.perform(get("/error").with(request -> { request.setDispatcherType(jakarta.servlet.DispatcherType.ERROR); return request; })
                .requestAttr(jakarta.servlet.RequestDispatcher.ERROR_STATUS_CODE, 500))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.status").value(500))
                .andExpect(jsonPath("$.exception").doesNotExist())
                .andExpect(jsonPath("$.trace").doesNotExist());
    }

    @Test void loginUsesSupportedDatabaseRoleAndNeverRepairsRolesImplicitly() throws Exception {
        User account = user("USER"); account.setPassword(passwords.encode("synthetic-login-password"));
        when(users.findByEmail(account.getEmail())).thenReturn(account);
        String body = "{\"email\":\"auth-fixture@example.test\",\"password\":\"synthetic-login-password\"}";
        for (String role : new String[]{"USER", "ADMIN"}) {
            account.setRole(role);
            var result = mvc.perform(post("/api/auth/login").contentType("application/json").content(body)).andExpect(status().isOk()).andReturn();
            String token = new com.fasterxml.jackson.databind.json.JsonMapper().readTree(result.getResponse().getContentAsString()).get("token").asText();
            assertTrue(jwt.validateToken(token)); assertEquals(role, jwt.extractRole(token));
        }
        for (String role : new String[]{null, "", "ROLE_ADMIN", "unexpected"}) {
            account.setRole(role);
            mvc.perform(post("/api/auth/login").contentType("application/json").content(body)).andExpect(status().isUnauthorized());
        }
        verify(users, never()).save(any());
    }
}
