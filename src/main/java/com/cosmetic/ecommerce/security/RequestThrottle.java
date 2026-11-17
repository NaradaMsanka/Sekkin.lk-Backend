package com.cosmetic.ecommerce.security;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Duration;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.Map;

/** Single-instance bounded abuse control. A shared gateway is required before horizontal scaling. */
public class RequestThrottle {
    private final Clock clock;
    private final int maximumBuckets;
    private final Map<String, Bucket> buckets = new HashMap<>();
    private long lastCleanup;

    public RequestThrottle() { this(Clock.systemUTC(), 10_000); }
    public RequestThrottle(Clock clock, int maximumBuckets) {
        if (maximumBuckets < 1) throw new IllegalArgumentException("Positive capacity required");
        this.clock = clock;
        this.maximumBuckets = maximumBuckets;
    }

    /** Returns zero when allowed; otherwise whole seconds until a retry. No input identities are retained. */
    public synchronized long attempt(String scope, String identity, int limit, Duration window) {
        if (limit < 1 || window.isNegative() || window.isZero()) throw new IllegalArgumentException("Invalid limit");
        long now = clock.millis();
        if (now - lastCleanup >= 60_000 || buckets.size() >= maximumBuckets) {
            buckets.entrySet().removeIf(entry -> entry.getValue().expiresAt <= now);
            lastCleanup = now;
        }
        String key = scope + ":" + digest(identity == null ? "unknown" : identity);
        Bucket bucket = buckets.get(key);
        if (bucket == null || bucket.expiresAt <= now) {
            // Fail closed at capacity rather than evicting active throttles for attacker-controlled identities.
            if (bucket == null && buckets.size() >= maximumBuckets) return 60;
            bucket = new Bucket(now + window.toMillis());
            buckets.put(key, bucket);
        }
        if (bucket.attempts >= limit) return Math.max(1, (bucket.expiresAt - now + 999) / 1000);
        bucket.attempts++;
        return 0;
    }

    private String digest(String identity) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(identity.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException impossible) { throw new IllegalStateException(impossible); }
    }

    private static final class Bucket {
        private final long expiresAt;
        private int attempts;
        private Bucket(long expiresAt) { this.expiresAt = expiresAt; }
    }
}
