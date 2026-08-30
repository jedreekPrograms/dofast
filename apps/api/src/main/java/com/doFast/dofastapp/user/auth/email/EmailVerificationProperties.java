package com.doFast.dofastapp.user.auth.email;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.time.Duration;
import java.util.Locale;

@Component
public class EmailVerificationProperties {
    private final Duration tokenTtl;
    private final Duration retention;
    private final String delivery;
    private final String verifyBaseUrl;
    private final String fromAddress;

    public EmailVerificationProperties(
            @Value("${dofast.security.email-verification.token-ttl-hours:24}") int tokenTtlHours,
            @Value("${dofast.security.email-verification.retention-days:7}") int retentionDays,
            @Value("${dofast.security.email-verification.delivery:disabled}") String delivery,
            @Value("${dofast.security.email-verification.verify-base-url:}") String verifyBaseUrl,
            @Value("${dofast.security.email-verification.from-address:}") String fromAddress
    ) {
        if (tokenTtlHours < 1 || tokenTtlHours > 72) throw new IllegalArgumentException("Email verification TTL must be between 1 and 72 hours");
        if (retentionDays < 1 || retentionDays > 30) throw new IllegalArgumentException("Email verification retention must be between 1 and 30 days");
        this.tokenTtl = Duration.ofHours(tokenTtlHours);
        this.retention = Duration.ofDays(retentionDays);
        this.delivery = normalizeDelivery(delivery);
        this.verifyBaseUrl = normalizeOptional(verifyBaseUrl);
        this.fromAddress = normalizeOptional(fromAddress);
        if (smtpEnabled()) validateSmtp();
    }

    public Duration tokenTtl() { return tokenTtl; }
    public Duration retention() { return retention; }
    public boolean smtpEnabled() { return "smtp".equals(delivery); }
    public String verifyBaseUrl() { return verifyBaseUrl; }
    public String fromAddress() { return fromAddress; }

    private void validateSmtp() {
        URI uri;
        try { uri = URI.create(verifyBaseUrl == null ? "" : verifyBaseUrl); }
        catch (IllegalArgumentException ex) { throw new IllegalArgumentException("Email verification base URL is invalid", ex); }
        if (!"https".equalsIgnoreCase(uri.getScheme()) || uri.getHost() == null || uri.getQuery() != null || uri.getFragment() != null) {
            throw new IllegalArgumentException("Email verification base URL must be absolute HTTPS without query or fragment");
        }
        if (fromAddress == null || fromAddress.length() > 320 || !fromAddress.contains("@")) {
            throw new IllegalArgumentException("Email verification from-address is invalid");
        }
    }

    private String normalizeDelivery(String value) {
        String normalized = value == null ? "disabled" : value.trim().toLowerCase(Locale.ROOT);
        if (!normalized.equals("disabled") && !normalized.equals("smtp")) throw new IllegalArgumentException("Email verification delivery must be disabled or smtp");
        return normalized;
    }

    private String normalizeOptional(String value) { return value == null || value.isBlank() ? null : value.trim(); }
}
