package com.cosmetic.ecommerce.service;

import com.cosmetic.ecommerce.config.PasswordResetProperties;
import com.cosmetic.ecommerce.security.RequestThrottle;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpStatus;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import java.time.Duration;
import java.util.Locale;
import java.util.concurrent.Executor;
import java.util.concurrent.RejectedExecutionException;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Service
public class PasswordResetDispatcher {
    private static final Logger LOG = LoggerFactory.getLogger(PasswordResetDispatcher.class);
    private final PasswordResetProperties settings;
    private final PasswordResetService resets;
    private final PasswordResetMailer mailer;
    private final RequestThrottle throttle;
    private final Executor executor;

    public PasswordResetDispatcher(PasswordResetProperties settings, PasswordResetService resets, PasswordResetMailer mailer,
                                   RequestThrottle throttle, @Qualifier("passwordResetExecutor") Executor executor) {
        this.settings = settings; this.resets = resets; this.mailer = mailer; this.throttle = throttle; this.executor = executor;
    }

    public void request(String email) {
        if (!settings.isEnabled()) throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Password recovery is temporarily unavailable. Please try again later.");
        String normalized = email.trim().toLowerCase(Locale.ROOT);
        // Apply to every address, including nonexistent accounts. Never reveal this account-level limit.
        if (throttle.attempt("password-reset-email", normalized, 3, Duration.ofHours(1)) > 0) return;
        try {
            executor.execute(() -> {
                try {
                    PasswordResetService.Delivery delivery = resets.issue(normalized);
                    if (delivery != null) mailer.send(delivery);
                } catch (Exception unavailable) {
                    // SMTP exceptions can contain credentials, addresses or message bodies. Do not log them.
                    LOG.warn("Password recovery delivery did not complete; check the mail provider and database health.");
                }
            });
        } catch (RejectedExecutionException busy) {
            LOG.warn("Password recovery queue is full; request was not queued.");
        }
    }

    @Scheduled(fixedDelay = 3_600_000, initialDelay = 3_600_000)
    public void cleanup() {
        if (!settings.isEnabled()) return;
        try { resets.purgeExpired(); }
        catch (RuntimeException unavailable) { LOG.warn("Password recovery token cleanup did not complete."); }
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void passwordChanged(PasswordResetService.PasswordChanged event) {
        if (!settings.isEnabled()) return;
        try {
            executor.execute(() -> {
                try { mailer.sendPasswordChanged(event.email()); }
                catch (Exception unavailable) { LOG.warn("Password change notification did not complete."); }
            });
        } catch (RejectedExecutionException busy) { LOG.warn("Password change notification was not queued."); }
    }
}
