package com.cosmetic.ecommerce.security;

import com.cosmetic.ecommerce.util.JwtUtil;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.support.PropertySourcesPlaceholderConfigurer;
import org.springframework.core.env.StandardEnvironment;
import org.springframework.core.io.ClassPathResource;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.Date;

import static org.junit.jupiter.api.Assertions.*;

class JwtConfigurationSafetyTest {
    // Public synthetic keys for isolated tests, never deployment credentials.
    private static final String TEST_KEY = "test-only-first-jwt-key-not-for-deployment-0001";
    private static final String ROTATED_TEST_KEY = "test-only-rotated-jwt-key-not-for-deployment-0002";

    private ApplicationContextRunner jwtContext() {
        return new ApplicationContextRunner()
                .withInitializer(context -> {
                    // Only a JWT bean is loaded; no datasource, Boot startup or external service.
                    var sources = context.getEnvironment().getPropertySources();
                    sources.remove(StandardEnvironment.SYSTEM_ENVIRONMENT_PROPERTY_SOURCE_NAME);
                    sources.remove(StandardEnvironment.SYSTEM_PROPERTIES_PROPERTY_SOURCE_NAME);
                    try {
                        for (var source : new YamlPropertySourceLoader().load("actual-application-config", new ClassPathResource("application.yml"))) {
                            sources.addLast(source);
                        }
                    } catch (IOException error) {
                        throw new UncheckedIOException(error);
                    }
                })
                .withBean(PropertySourcesPlaceholderConfigurer.class)
                .withUserConfiguration(JwtUtil.class);
    }

    @Test
    void actualYamlRequiresTheRuntimeSecretAtContextStartup() {
        jwtContext().run(context -> assertNotNull(context.getStartupFailure()));
    }

    @Test
    void actualYamlWiresOnlyTheExplicitSyntheticRuntimeSecret() {
        jwtContext().withPropertyValues("JWT_SECRET=" + TEST_KEY).run(context -> {
            assertNull(context.getStartupFailure());
            JwtUtil jwt = context.getBean(JwtUtil.class);
            assertTrue(jwt.validateToken(jwt.generateToken("fixture@example.test", "USER")));
        });
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {" ", "too-short"})
    void refusesMissingOrWeakSigningConfiguration(String secret) {
        assertThrows(IllegalArgumentException.class, () -> new JwtUtil(secret));
    }

    @Test
    void configuredKeySignsAndValidatesExpectedAccountClaims() {
        JwtUtil jwt = new JwtUtil(TEST_KEY);
        String token = jwt.generateToken("fixture@example.test", "USER");
        assertTrue(jwt.validateToken(token));
        assertEquals("fixture@example.test", jwt.extractEmail(token));
        assertEquals("USER", jwt.extractRole(token));
        assertEquals(0L, jwt.extractSessionVersion(token));
    }

    @Test
    void rotationRejectsTokensFromThePreviousKey() {
        String oldToken = new JwtUtil(TEST_KEY).generateToken("fixture@example.test", "USER");
        assertFalse(new JwtUtil(ROTATED_TEST_KEY).validateToken(oldToken));
    }

    @Test
    void rejectsMalformedAndExpiredTokens() {
        JwtUtil jwt = new JwtUtil(TEST_KEY);
        assertFalse(jwt.validateToken("not-a-token"));
        String expired = Jwts.builder().setSubject("fixture@example.test")
                .setExpiration(new Date(System.currentTimeMillis() - 60000))
                .signWith(Keys.hmacShaKeyFor(TEST_KEY.getBytes(StandardCharsets.UTF_8)), SignatureAlgorithm.HS256)
                .compact();
        assertFalse(jwt.validateToken(expired));
    }

    @Test
    void signsTheExplicitCurrentSessionVersionAndRejectsNegativeVersions() {
        JwtUtil jwt = new JwtUtil(TEST_KEY);
        String token = jwt.generateToken("fixture@example.test", "USER", 17);
        assertTrue(jwt.validateToken(token)); assertEquals(17L, jwt.extractSessionVersion(token));
        assertThrows(IllegalArgumentException.class, () -> jwt.generateToken("fixture@example.test", "USER", -1));
    }

    @ParameterizedTest
    @ValueSource(ints = {32, 48, 64, 88})
    void supportsSyntheticSigningKeyLengthsIncludingTheDocumentedGenerationFormat(int bytes) {
        // Public deterministic fixtures; the documented 64-random-byte Base64 format is 88 UTF-8 bytes.
        JwtUtil jwt = new JwtUtil("x".repeat(bytes));
        String token = jwt.generateToken("fixture@example.test", "USER", 2);
        assertTrue(jwt.validateToken(token));
        assertEquals(2L, jwt.extractSessionVersion(token));
    }

    @Test
    void rejectsLegacyAndMalformedSessionVersionClaims() {
        JwtUtil jwt = new JwtUtil(TEST_KEY);
        for (Object version : new Object[]{null, -1, 1.5, "0"}) {
            String token = Jwts.builder().setSubject("fixture@example.test").claim("sessionVersion", version)
                    .setExpiration(new Date(System.currentTimeMillis() + 60000))
                    .signWith(Keys.hmacShaKeyFor(TEST_KEY.getBytes(StandardCharsets.UTF_8)), SignatureAlgorithm.HS256).compact();
            assertFalse(jwt.validateToken(token)); assertNull(jwt.extractSessionVersion(token));
        }
    }
}
