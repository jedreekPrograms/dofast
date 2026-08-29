package com.doFast.dofastapp.payout.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.util.Locale;

@Component
public class StripeConnectProperties {

    private final boolean enabled;
    private final String country;
    private final String refreshUrl;
    private final String returnUrl;

    public StripeConnectProperties(
            @Value("${dofast.payouts.stripe-connect.enabled:false}") boolean enabled,
            @Value("${dofast.payouts.stripe-connect.country:PL}") String country,
            @Value("${dofast.payouts.stripe-connect.refresh-url:}") String refreshUrl,
            @Value("${dofast.payouts.stripe-connect.return-url:}") String returnUrl
    ) {
        this.enabled = enabled;
        this.country = normalizeCountry(country);
        this.refreshUrl = normalizeUrl(refreshUrl, "refresh-url", enabled);
        this.returnUrl = normalizeUrl(returnUrl, "return-url", enabled);
    }

    public boolean enabled() { return enabled; }
    public String country() { return country; }
    public String refreshUrl() { return refreshUrl; }
    public String returnUrl() { return returnUrl; }

    private String normalizeCountry(String value) {
        if (value == null || value.isBlank()) return "PL";
        String normalized = value.trim().toUpperCase(Locale.ROOT);
        if (!normalized.matches("[A-Z]{2}")) {
            throw new IllegalArgumentException("Stripe Connect country must be a two-letter ISO code");
        }
        return normalized;
    }

    private String normalizeUrl(String value, String name, boolean required) {
        if (value == null || value.isBlank()) {
            if (required) throw new IllegalArgumentException("Stripe Connect " + name + " is required when onboarding is enabled");
            return "";
        }
        String normalized = value.trim();
        URI uri;
        try {
            uri = URI.create(normalized);
        } catch (IllegalArgumentException ex) {
            throw new IllegalArgumentException("Invalid Stripe Connect " + name, ex);
        }
        String scheme = uri.getScheme();
        String host = uri.getHost();
        boolean localhost = "localhost".equalsIgnoreCase(host) || "127.0.0.1".equals(host);
        if (host == null || scheme == null || !("https".equalsIgnoreCase(scheme) || (localhost && "http".equalsIgnoreCase(scheme)))) {
            throw new IllegalArgumentException("Stripe Connect " + name + " must use HTTPS outside localhost");
        }
        return normalized;
    }
}
