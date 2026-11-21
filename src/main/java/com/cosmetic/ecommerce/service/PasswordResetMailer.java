package com.cosmetic.ecommerce.service;

import com.cosmetic.ecommerce.config.PasswordResetProperties;
import jakarta.mail.MessagingException;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;
import org.springframework.web.util.HtmlUtils;
import java.nio.charset.StandardCharsets;

@Service
public class PasswordResetMailer {
    private final JavaMailSender mail;
    private final PasswordResetProperties settings;
    public PasswordResetMailer(JavaMailSender mail, PasswordResetProperties settings) { this.mail = mail; this.settings = settings; }

    public void send(PasswordResetService.Delivery delivery) throws MessagingException {
        var message = mail.createMimeMessage();
        var helper = new MimeMessageHelper(message, true, StandardCharsets.UTF_8.name());
        helper.setFrom(settings.getFrom()); helper.setTo(delivery.email());
        helper.setSubject("Reset your SEKKIN.LK password");
        String url = HtmlUtils.htmlEscape(delivery.url());
        String plain = "SEKKIN.LK\n\nWe received a request to reset your password.\n\nReset your password: " + delivery.url()
                + "\n\nThis link expires in 20 minutes and can only be used once."
                + "\nIf you did not request this, you can ignore this email. Your password will remain unchanged.";
        String html = """
                <!doctype html><html lang="en"><head><meta name="viewport" content="width=device-width, initial-scale=1"><meta name="referrer" content="no-referrer"></head>
                <body style="margin:0;padding:24px 12px;background:#fbf7f5;font-family:Arial,sans-serif;color:#302326">
                <table role="presentation" style="width:100%%;max-width:560px;margin:auto;border-collapse:collapse;background:#ffffff;border:1px solid #e9dfda">
                <tr><td style="padding:28px 28px 18px;border-top:5px solid #8f344d;font-size:22px;font-weight:bold;color:#8f344d">SEKKIN.LK</td></tr>
                <tr><td style="padding:0 28px 28px;font-size:16px;line-height:1.7">
                <h1 style="font-size:26px;line-height:1.3;margin:12px 0 20px">Reset your password</h1>
                <p>We received a request to reset the password for your SEKKIN.LK account.</p>
                <p style="margin:28px 0"><a href="%s" style="display:inline-block;padding:14px 24px;background:#8f344d;color:#ffffff;text-decoration:none;border-radius:6px;font-weight:bold">Reset password</a></p>
                <p>This secure link expires in <strong>20 minutes</strong> and can only be used once.</p>
                <p>If you did not request a password reset, you can ignore this email. Your password will remain unchanged.</p>
                <p style="margin-top:28px;font-size:14px;color:#756661">If the button does not work, copy this link into your browser:</p>
                <p style="font-size:14px;word-break:break-all"><a href="%s" style="color:#8f344d">%s</a></p>
                </td></tr></table></body></html>
                """.formatted(url, url, url);
        helper.setText(plain, html);
        message.setHeader("X-Auto-Response-Suppress", "All");
        mail.send(message);
    }

    public void sendPasswordChanged(String email) throws MessagingException {
        var message = mail.createMimeMessage();
        var helper = new MimeMessageHelper(message, StandardCharsets.UTF_8.name());
        helper.setFrom(settings.getFrom()); helper.setTo(email);
        helper.setSubject("Your SEKKIN.LK password has changed");
        helper.setText("SEKKIN.LK\n\nYour account password has been changed. All previous sign-in sessions have been revoked."
                + "\n\nIf you did not make this change, request another password reset and contact our support team immediately."
                + "\n\nYour password is never included in email.");
        mail.send(message);
    }
}
