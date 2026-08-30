package com.doFast.dofastapp.user.auth.session;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.Duration;

@Component
public class AuthSessionProperties {

    private final Duration refreshTtl;
    private final Duration reuseGrace;
    private final Duration retention;
    private final boolean cookieSecure;
    private final String sameSite;

    public AuthSessionProperties(
            @Value("${dofast.security.session.refresh-ttl-days:30}") int refreshTtlDays,
            @Value("${dofast.security.session.reuse-grace-seconds:15}") int reuseGraceSeconds,
            @Value("${dofast.security.session.retention-days:7}") int retentionDays,
            @Value("${dofast.security.session.cookie-secure:false}") boolean cookieSecure,
            @Value("${dofast.security.session.same-site:Strict}") String sameSite
    ) {
        if (refreshTtlDays < 1 || refreshTtlDays > 90) {
            throw new IllegalArgumentException("Refresh session TTL must be between 1 and 90 days");
        }
        if (reuseGraceSeconds < 0 || reuseGraceSeconds > 120) {
            throw new IllegalArgumentException("Refresh token reuse grace must be between 0 and 120 seconds");
        }
        if (retentionDays < 1 || retentionDays > 30) {
            throw new IllegalArgumentException("Refresh session retention must be between 1 and 30 days");
        }
        String normalizedSameSite = normalizeSameSite(sameSite);
        this.refreshTtl = Duration.ofDays(refreshTtlDays);
        this.reuseGrace = Duration.ofSeconds(reuseGraceSeconds);
        this.retention = Duration.ofDays(retentionDays);
        this.cookieSecure = cookieSecure;
        this.sameSite = normalizedSameSite;
    }

    public Duration refreshTtl() { return refreshTtl; }
    public Duration reuseGrace() { return reuseGrace; }
    public Duration retention() { return retention; }
    public boolean cookieSecure() { return cookieSecure; }
    public String sameSite() { return sameSite; }

    private String normalizeSameSite(String value) {
        if (value == null) return "Strict";
        return switch (value.trim().toLowerCase()) {
            case "strict" -> "Strict";
            case "lax" -> "Lax";
            default -> throw new IllegalArgumentException("Session cookie SameSite must be Strict or Lax");
        };
    }
}
