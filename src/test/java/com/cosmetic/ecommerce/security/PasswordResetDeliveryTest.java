package com.cosmetic.ecommerce.security;

import com.cosmetic.ecommerce.config.PasswordResetProperties;
import com.cosmetic.ecommerce.service.*;
import jakarta.mail.Session;
import jakarta.mail.internet.MimeMessage;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.system.*;
import org.springframework.mail.MailSendException;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mock.web.*;
import com.cosmetic.ecommerce.config.AbuseRateLimitFilter;
import org.springframework.web.server.ResponseStatusException;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(OutputCaptureExtension.class)
class PasswordResetDeliveryTest {
    private PasswordResetProperties settings() { var props = new PasswordResetProperties(); props.setEnabled(true); props.validate(); return props; }
    @Test void mailIsMultipartBrandedAndUsesOnlyTrustedHttpsUrl() throws Exception {
        JavaMailSender sender = mock(JavaMailSender.class);
        MimeMessage mime = new MimeMessage(Session.getInstance(new Properties())); when(sender.createMimeMessage()).thenReturn(mime);
        var settings = settings();
        String url = settings.resetUrl("a".repeat(43));
        new PasswordResetMailer(sender, settings).send(new PasswordResetService.Delivery("synthetic@example.test", url));
        mime.saveChanges();
        assertEquals("no-reply@sekkin.lk", mime.getFrom()[0].toString());
        assertEquals("Reset your SEKKIN.LK password", mime.getSubject());
        var bytes = new java.io.ByteArrayOutputStream(); mime.writeTo(bytes);
        String message = bytes.toString(java.nio.charset.StandardCharsets.UTF_8);
        assertTrue(message.contains("multipart/"));
        // Quoted-printable transfer encoding may encode the equals sign in #token=.
        var multipart = (jakarta.mail.Multipart) mime.getContent();
        StringBuilder contents = new StringBuilder(); collect(multipart, contents);
        assertTrue(contents.toString().contains("20 minutes")); assertTrue(contents.toString().contains(url));
        assertTrue(contents.toString().contains("ignore this email"));
        assertFalse(contents.toString().contains("<script")); assertFalse(contents.toString().contains("<img"));
        verify(sender).send(mime);
    }
    private void collect(jakarta.mail.Multipart parts, StringBuilder text) throws Exception {
        for (int i = 0; i < parts.getCount(); i++) {
            Object value = parts.getBodyPart(i).getContent();
            if (value instanceof jakarta.mail.Multipart nested) collect(nested, text); else text.append(value);
        }
    }
    @Test void smtpFailureNeverLeaksRecipientTokenOrExceptionDetails(CapturedOutput output) throws Exception {
        PasswordResetService service = mock(PasswordResetService.class); PasswordResetMailer mailer = mock(PasswordResetMailer.class);
        var delivery = new PasswordResetService.Delivery("sensitive-recipient@example.test", "https://www.sekkin.lk/reset-password#token=sensitive-reset-token");
        when(service.issue(any())).thenReturn(delivery);
        doThrow(new MailSendException("sensitive-smtp-password sensitive-reset-token sensitive-recipient@example.test")).when(mailer).send(delivery);
        var dispatcher = new PasswordResetDispatcher(settings(), service, mailer, new RequestThrottle(), Runnable::run);
        assertDoesNotThrow(() -> dispatcher.request(delivery.email()));
        assertFalse(output.getAll().contains("sensitive-smtp-password"));
        assertFalse(output.getAll().contains("sensitive-reset-token"));
        assertFalse(output.getAll().contains("sensitive-recipient@example.test"));
    }
    @Test void knownAndUnknownRequestsQueueWorkWithoutWaitingForLookup() {
        PasswordResetService service = mock(PasswordResetService.class); PasswordResetMailer mailer = mock(PasswordResetMailer.class);
        List<Runnable> queue = new ArrayList<>();
        var dispatcher = new PasswordResetDispatcher(settings(), service, mailer, new RequestThrottle(), queue::add);
        dispatcher.request("known@example.test"); dispatcher.request("unknown@example.test");
        assertEquals(2, queue.size()); verifyNoInteractions(service, mailer);
        queue.forEach(Runnable::run); verify(service).issue("known@example.test"); verify(service).issue("unknown@example.test");
        verifyNoInteractions(mailer);
    }
    @Test void accountRateLimitAppliesAcrossIpAndCaseVariantsButStaysGeneric() {
        PasswordResetService service = mock(PasswordResetService.class); PasswordResetMailer mailer = mock(PasswordResetMailer.class);
        AtomicInteger jobs = new AtomicInteger();
        var dispatcher = new PasswordResetDispatcher(settings(), service, mailer, new RequestThrottle(), task -> jobs.incrementAndGet());
        for (int i = 0; i < 4; i++) assertDoesNotThrow(() -> dispatcher.request(" MixedCase@example.test "));
        assertEquals(3, jobs.get());
        dispatcher.request("another@example.test"); assertEquals(4, jobs.get());
    }
    @Test void queueSaturationAndDisabledConfigurationDoNotEnumerateAccounts() {
        var dispatcher = new PasswordResetDispatcher(settings(), mock(PasswordResetService.class), mock(PasswordResetMailer.class), new RequestThrottle(),
                task -> { throw new RejectedExecutionException("sensitive queue input"); });
        assertDoesNotThrow(() -> dispatcher.request("known@example.test"));
        var disabled = new PasswordResetDispatcher(new PasswordResetProperties(), mock(PasswordResetService.class), mock(PasswordResetMailer.class), new RequestThrottle(), Runnable::run);
        assertEquals(503, assertThrows(ResponseStatusException.class, () -> disabled.request("unknown@example.test")).getStatusCode().value());
    }
    @Test void ipLimitsCannotBeBypassedWithUntrustedForwardedHeaders() throws Exception {
        AbuseRateLimitFilter filter = new AbuseRateLimitFilter(new RequestThrottle()); AtomicInteger handled = new AtomicInteger();
        for (int i = 0; i < 11; i++) {
            var request = new MockHttpServletRequest("POST", "/api/auth/forgot-password");
            request.setRemoteAddr("192.0.2.2"); request.addHeader("X-Forwarded-For", "198.51.100." + i);
            var response = new MockHttpServletResponse(); filter.doFilter(request, response, (req, res) -> handled.incrementAndGet());
            if (i == 10) { assertEquals(429, response.getStatus()); assertNotNull(response.getHeader("Retry-After")); }
        }
        assertEquals(10, handled.get());
    }
    @Test void rejectsUntrustedFrontendUrlsAndSenderHeaderInjection() {
        for (String url : List.of("http://www.sekkin.lk", "https://example.test@attacker.test", "https://www.sekkin.lk/path", "https://www.sekkin.lk?redirect=evil", "https://www.sekkin.lk#fragment")) {
            var settings = settings(); settings.setFrontendUrl(url); assertThrows(IllegalStateException.class, settings::validate);
        }
        var settings = settings(); settings.setFrom("no-reply@sekkin.lk\r\nBcc: victim@example.test");
        assertThrows(IllegalStateException.class, settings::validate);
    }
}
