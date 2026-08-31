package com.doFast.dofastapp.user.auth.password;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.time.Duration;
import java.util.Locale;

@Component
public class PasswordRecoveryProperties {

    private final Duration tokenTtl;
    private final Duration retention;
    private final Duration requestCooldown;
    private final String delivery;
    private final String resetBaseUrl;
    private final String fromAddress;

    public PasswordRecoveryProperties(
            @Value("${dofast.security.password-recovery.token-ttl-minutes:30}") int tokenTtlMinutes,
            @Value("${dofast.security.password-recovery.retention-days:7}") int retentionDays,
            @Value("${dofast.security.password-recovery.request-cooldown-seconds:60}") int requestCooldownSeconds,
            @Value("${dofast.security.password-recovery.delivery:disabled}") String delivery,
            @Value("${dofast.security.password-recovery.reset-base-url:}") String resetBaseUrl,
            @Value("${dofast.security.password-recovery.from-address:}") String fromAddress
    ) {
        if (tokenTtlMinutes < 5 || tokenTtlMinutes > 60) {
            throw new IllegalArgumentException("Password reset token TTL must be between 5 and 60 minutes");
        }
        if (retentionDays < 1 || retentionDays > 30) {
            throw new IllegalArgumentException("Password reset token retention must be between 1 and 30 days");
        }
        if (requestCooldownSeconds < 15 || requestCooldownSeconds > 900) {
            throw new IllegalArgumentException("Password reset request cooldown must be between 15 and 900 seconds");
        }
        this.delivery = normalizeDelivery(delivery);
        this.resetBaseUrl = normalizeOptional(resetBaseUrl);
        this.fromAddress = normalizeOptional(fromAddress);
        this.tokenTtl = Duration.ofMinutes(tokenTtlMinutes);
        this.retention = Duration.ofDays(retentionDays);
        this.requestCooldown = Duration.ofSeconds(requestCooldownSeconds);

        if (smtpEnabled()) {
            validateProductionDeliverySettings();
        }
    }

    public Duration tokenTtl() { return tokenTtl; }
    public Duration retention() { return retention; }
    public Duration requestCooldown() { return requestCooldown; }
    public boolean smtpEnabled() { return "smtp".equals(delivery); }
    public String resetBaseUrl() { return resetBaseUrl; }
    public String fromAddress() { return fromAddress; }

    private void validateProductionDeliverySettings() {
        if (resetBaseUrl == null) {
            throw new IllegalArgumentException("Password reset base URL is required for SMTP delivery");
        }
        URI uri;
        try {
            uri = URI.create(resetBaseUrl);
        } catch (IllegalArgumentException ex) {
            throw new IllegalArgumentException("Password reset base URL is invalid", ex);
        }
        if (!"https".equalsIgnoreCase(uri.getScheme()) || uri.getHost() == null
                || uri.getQuery() != null || uri.getFragment() != null) {
            throw new IllegalArgumentException("Password reset base URL must be an absolute HTTPS URL without query or fragment");
        }
        if (fromAddress == null || fromAddress.length() > 320 || !fromAddress.contains("@")) {
            throw new IllegalArgumentException("Password recovery from-address is invalid");
        }
    }

    private String normalizeDelivery(String value) {
        String normalized = value == null ? "disabled" : value.trim().toLowerCase(Locale.ROOT);
        if (!normalized.equals("disabled") && !normalized.equals("smtp")) {
            throw new IllegalArgumentException("Password recovery delivery must be disabled or smtp");
        }
        return normalized;
    }

    private String normalizeOptional(String value) {
        if (value == null || value.isBlank()) return null;
        return value.trim();
    }
}
