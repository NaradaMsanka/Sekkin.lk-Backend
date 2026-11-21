package com.cosmetic.ecommerce.config;

import jakarta.annotation.PostConstruct;
import org.springframework.boot.context.properties.ConfigurationProperties;
import java.net.URI;

@ConfigurationProperties("app.password-reset")
public class PasswordResetProperties {
    private boolean enabled;
    private String frontendUrl = "https://www.sekkin.lk";
    private String from = "no-reply@sekkin.lk";

    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }
    public String getFrontendUrl() { return frontendUrl; }
    public void setFrontendUrl(String frontendUrl) { this.frontendUrl = frontendUrl; }
    public String getFrom() { return from; }
    public void setFrom(String from) { this.from = from; }

    @PostConstruct
    public void validate() {
        if (!enabled) return;
        try {
            URI uri = URI.create(frontendUrl);
            if (!"https".equals(uri.getScheme()) || uri.getHost() == null || uri.getUserInfo() != null
                    || uri.getQuery() != null || uri.getFragment() != null
                    || !(uri.getPath().isEmpty() || "/".equals(uri.getPath()))) throw new IllegalArgumentException();
            jakarta.mail.internet.InternetAddress address = new jakarta.mail.internet.InternetAddress(from, true);
            address.validate();
            if (!address.getAddress().equals(from) || !from.contains("@") || from.contains("\r") || from.contains("\n"))
                throw new IllegalArgumentException();
        } catch (Exception invalid) {
            throw new IllegalStateException("Password reset requires an HTTPS frontend origin and a valid sender address.");
        }
    }

    public String resetUrl(String token) {
        // The fragment is read by React and never sent in HTTP request URLs/access logs.
        return frontendUrl.replaceAll("/$", "") + "/reset-password#token=" + token;
    }
}
