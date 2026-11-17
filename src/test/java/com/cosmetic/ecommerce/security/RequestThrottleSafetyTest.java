package com.cosmetic.ecommerce.security;

import com.cosmetic.ecommerce.config.AbuseRateLimitFilter;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import java.time.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import static org.junit.jupiter.api.Assertions.*;

class RequestThrottleSafetyTest {
    @Test void admitsTheLimitThenReturnsRetryAfterAndResetsOnExpiry() {
        MutableClock clock = new MutableClock(); RequestThrottle throttle = new RequestThrottle(clock, 100);
        assertEquals(0, throttle.attempt("test", "identity", 2, Duration.ofMinutes(1)));
        assertEquals(0, throttle.attempt("test", "identity", 2, Duration.ofMinutes(1)));
        assertEquals(60, throttle.attempt("test", "identity", 2, Duration.ofMinutes(1)));
        clock.millis += 60_000;
        assertEquals(0, throttle.attempt("test", "identity", 2, Duration.ofMinutes(1)));
    }

    @Test void keepsScopesAndIdentitiesIndependent() {
        RequestThrottle throttle = new RequestThrottle();
        assertEquals(0, throttle.attempt("login", "one", 1, Duration.ofMinutes(1)));
        assertEquals(0, throttle.attempt("register", "one", 1, Duration.ofMinutes(1)));
        assertEquals(0, throttle.attempt("login", "two", 1, Duration.ofMinutes(1)));
        assertTrue(throttle.attempt("login", "one", 1, Duration.ofMinutes(1)) > 0);
    }

    @Test void boundsMemoryWithoutEvictingAnActiveThrottle() {
        MutableClock clock = new MutableClock(); RequestThrottle throttle = new RequestThrottle(clock, 1);
        assertEquals(0, throttle.attempt("login", "one", 1, Duration.ofSeconds(5)));
        assertTrue(throttle.attempt("login", "two", 1, Duration.ofSeconds(5)) > 0);
        assertTrue(throttle.attempt("login", "one", 1, Duration.ofSeconds(5)) > 0);
        clock.millis += 5000;
        assertEquals(0, throttle.attempt("login", "two", 1, Duration.ofSeconds(5)));
    }

    @Test void concurrentRequestsCannotOverrunTheAllowance() throws Exception {
        RequestThrottle throttle = new RequestThrottle(); AtomicInteger allowed = new AtomicInteger();
        try (ExecutorService executor = Executors.newFixedThreadPool(8)) {
            for (int i = 0; i < 100; i++) executor.submit(() -> {
                if (throttle.attempt("login", "one", 10, Duration.ofMinutes(1)) == 0) allowed.incrementAndGet();
            });
        }
        assertEquals(10, allowed.get());
    }

    @Test void loginFilterLimitsBeforeTheHandlerAndDoesNotTrustForwardedFor() throws Exception {
        AbuseRateLimitFilter filter = new AbuseRateLimitFilter(new RequestThrottle()); AtomicInteger handled = new AtomicInteger();
        for (int i = 0; i < 31; i++) {
            MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/auth/login");
            request.setRemoteAddr("192.0.2.10"); request.addHeader("X-Forwarded-For", "198.51.100." + i);
            MockHttpServletResponse response = new MockHttpServletResponse();
            filter.doFilter(request, response, (req, res) -> handled.incrementAndGet());
            if (i == 30) {
                assertEquals(429, response.getStatus()); assertNotNull(response.getHeader("Retry-After"));
                assertFalse(response.getContentAsString().contains("192.0.2"));
            }
        }
        assertEquals(30, handled.get());
    }

    @Test void throttlesRegistrationAndBothOrderEntryPointsIndependently() throws Exception {
        AbuseRateLimitFilter filter = new AbuseRateLimitFilter(new RequestThrottle());
        for (String path : new String[]{"/api/auth/register", "/api/orders", "/api/orders/direct"}) {
            int allowance = path.contains("register") ? 10 : 30;
            for (int i = 0; i <= allowance; i++) {
                MockHttpServletRequest request = new MockHttpServletRequest("POST", path);
                request.setRemoteAddr(path); // Isolated synthetic source per path.
                MockHttpServletResponse response = new MockHttpServletResponse();
                filter.doFilter(request, response, (req, res) -> {});
                assertEquals(i == allowance ? 429 : 200, response.getStatus());
            }
        }
    }

    @Test void publicReadsAndPreflightAreNotThrottled() throws Exception {
        AbuseRateLimitFilter filter = new AbuseRateLimitFilter(new RequestThrottle()); AtomicInteger handled = new AtomicInteger();
        for (int i = 0; i < 100; i++) {
            filter.doFilter(new MockHttpServletRequest("GET", "/api/products"), new MockHttpServletResponse(), (req, res) -> handled.incrementAndGet());
            filter.doFilter(new MockHttpServletRequest("OPTIONS", "/api/auth/login"), new MockHttpServletResponse(), (req, res) -> handled.incrementAndGet());
        }
        assertEquals(200, handled.get());
    }

    private static class MutableClock extends Clock {
        private long millis = 100_000;
        public ZoneId getZone() { return ZoneOffset.UTC; }
        public Clock withZone(ZoneId zone) { return this; }
        public Instant instant() { return Instant.ofEpochMilli(millis); }
        public long millis() { return millis; }
    }
}
