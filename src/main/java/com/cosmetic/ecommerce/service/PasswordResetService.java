package com.cosmetic.ecommerce.service;

import com.cosmetic.ecommerce.config.PasswordResetProperties;
import com.cosmetic.ecommerce.entity.PasswordResetToken;
import com.cosmetic.ecommerce.entity.User;
import com.cosmetic.ecommerce.repository.*;
import com.cosmetic.ecommerce.validation.BcryptPasswordLengthValidator;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.time.*;
import java.util.*;

@Service
public class PasswordResetService {
    public static final String INVALID_LINK = "This reset link is invalid or has expired. Request a new link.";
    public static final Duration TOKEN_LIFETIME = Duration.ofMinutes(20);
    private final UserRepository users;
    private final PasswordResetTokenRepository tokens;
    private final PasswordEncoder passwords;
    private final PasswordResetProperties settings;
    private final Clock clock;
    private final ApplicationEventPublisher events;
    private final SecureRandom random = new SecureRandom();

    public PasswordResetService(UserRepository users, PasswordResetTokenRepository tokens, PasswordEncoder passwords,
                                PasswordResetProperties settings, Clock clock, ApplicationEventPublisher events) {
        this.users = users; this.tokens = tokens; this.passwords = passwords; this.settings = settings; this.clock = clock; this.events = events;
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public Delivery issue(String email) {
        var matches = users.findIdsByEmailIgnoreCase(email);
        // Do not guess an owner if legacy data contains case-variant duplicate email addresses.
        if (matches.size() != 1) return null;
        User user = users.findByIdForUpdate(matches.getFirst()).orElse(null);
        if (!eligible(user)) return null;
        byte[] bytes = new byte[32]; random.nextBytes(bytes);
        String raw = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        Instant now = clock.instant();
        PasswordResetToken token = new PasswordResetToken();
        token.setTokenHash(hash(raw)); token.setUser(user); token.setCreatedAt(now); token.setExpiresAt(now.plus(TOKEN_LIFETIME));
        tokens.save(token);
        // Existing links remain valid until use/expiry: unauthenticated requests cannot invalidate them.
        return new Delivery(user.getEmail(), settings.resetUrl(raw));
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public void reset(String raw, String newPassword, String confirmation) {
        if (newPassword == null || newPassword.isBlank() || newPassword.length() < 6
                || !new BcryptPasswordLengthValidator().isValid(newPassword, null))
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Use at least 6 characters and no more than 72 UTF-8 bytes for your password.");
        if (!newPassword.equals(confirmation))
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Passwords do not match.");
        if (raw == null || !raw.matches("[A-Za-z0-9_-]{43}")) throw invalidLink();
        String digest = hash(raw);
        Long owner = tokens.findOwnerId(digest);
        if (owner == null) throw invalidLink();
        // Every reset for an account takes this same lock, including resets with different tokens.
        User user = users.findByIdForUpdate(owner).orElse(null);
        PasswordResetToken token = tokens.findById(digest).orElse(null);
        Instant now = clock.instant();
        if (!eligible(user) || token == null || token.getUsedAt() != null || !token.getExpiresAt().isAfter(now)
                || user.getSessionVersion() == Long.MAX_VALUE) throw invalidLink();
        user.setPassword(passwords.encode(newPassword));
        user.setSessionVersion(user.getSessionVersion() + 1);
        users.save(user);
        tokens.consumeAll(owner, now);
        events.publishEvent(new PasswordChanged(user.getEmail()));
        // Password, session revocation and consumption commit or roll back together.
    }

    @Transactional
    public void purgeExpired() { tokens.deleteExpiredBefore(clock.instant().minus(Duration.ofDays(1))); }

    public static String hash(String raw) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(raw.getBytes(StandardCharsets.UTF_8))); }
        catch (NoSuchAlgorithmException impossible) { throw new IllegalStateException("Required hash unavailable"); }
    }
    private static boolean eligible(User user) {
        return user != null && user.isEnabled() && ("USER".equals(user.getRole()) || "ADMIN".equals(user.getRole()));
    }
    private static ResponseStatusException invalidLink() { return new ResponseStatusException(HttpStatus.BAD_REQUEST, INVALID_LINK); }

    public record Delivery(String email, String url) {
        @Override public String toString() { return "PasswordResetDelivery[REDACTED]"; }
    }
    public record PasswordChanged(String email) {
        @Override public String toString() { return "PasswordChanged[REDACTED]"; }
    }
}
