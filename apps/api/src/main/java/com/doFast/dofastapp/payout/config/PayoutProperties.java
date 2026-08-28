package com.doFast.dofastapp.payout.config;

import com.doFast.dofastapp.common.exception.BusinessException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.util.Locale;

@Component
public class PayoutProperties {

    private final String provider;
    private final boolean sandboxEnabled;
    private final BigDecimal minimumAmount;
    private final int maxAttempts;
    private final long retryBaseSeconds;
    private final long staleProcessingSeconds;

    public PayoutProperties(
            @Value("${dofast.payouts.provider:disabled}") String provider,
            @Value("${dofast.payouts.sandbox-enabled:false}") boolean sandboxEnabled,
            @Value("${dofast.payouts.minimum-amount:1.00}") BigDecimal minimumAmount,
            @Value("${dofast.payouts.max-attempts:5}") int maxAttempts,
            @Value("${dofast.payouts.retry-base-seconds:15}") long retryBaseSeconds,
            @Value("${dofast.payouts.stale-processing-seconds:300}") long staleProcessingSeconds
    ) {
        this.provider = normalizeProvider(provider);
        this.sandboxEnabled = sandboxEnabled;
        this.minimumAmount = normalizeMoney(minimumAmount);
        if (this.minimumAmount.signum() <= 0) throw new IllegalArgumentException("Payout minimum must be positive");
        if (maxAttempts < 1 || maxAttempts > 20) throw new IllegalArgumentException("Payout max attempts must be between 1 and 20");
        if (retryBaseSeconds < 1 || retryBaseSeconds > 3600) throw new IllegalArgumentException("Invalid payout retry base seconds");
        if (staleProcessingSeconds < 30 || staleProcessingSeconds > 86400) throw new IllegalArgumentException("Invalid payout stale-processing timeout");
        this.maxAttempts = maxAttempts;
        this.retryBaseSeconds = retryBaseSeconds;
        this.staleProcessingSeconds = staleProcessingSeconds;
    }

    public String provider() { return provider; }
    public boolean sandboxEnabled() { return sandboxEnabled; }
    public BigDecimal minimumAmount() { return minimumAmount; }
    public int maxAttempts() { return maxAttempts; }
    public Duration staleProcessingTimeout() { return Duration.ofSeconds(staleProcessingSeconds); }

    public Duration retryDelay(int attemptCount) {
        int exponent = Math.max(0, Math.min(attemptCount - 1, 8));
        long seconds = Math.min(retryBaseSeconds * (1L << exponent), 3600L);
        return Duration.ofSeconds(seconds);
    }

    public BigDecimal normalizeRequestedAmount(BigDecimal amount) {
        if (amount == null || amount.signum() <= 0) {
            throw new BusinessException("Kwota wypłaty musi być dodatnia");
        }
        try {
            BigDecimal normalized = amount.setScale(2, RoundingMode.UNNECESSARY);
            if (normalized.compareTo(minimumAmount) < 0) {
                throw new BusinessException("Minimalna kwota wypłaty to " + minimumAmount + " PLN");
            }
            return normalized;
        } catch (ArithmeticException ex) {
            throw new BusinessException("Kwota wypłaty może mieć maksymalnie dwa miejsca po przecinku");
        }
    }

    public String providerMode() {
        if ("disabled".equals(provider)) return "DISABLED";
        if ("sandbox".equals(provider)) return "SANDBOX";
        return "LIVE";
    }

    private String normalizeProvider(String value) {
        if (value == null || value.isBlank()) return "disabled";
        String normalized = value.trim().toLowerCase(Locale.ROOT);
        if (normalized.length() > 32) throw new IllegalArgumentException("Payout provider code is too long");
        return normalized;
    }

    private BigDecimal normalizeMoney(BigDecimal amount) {
        if (amount == null) throw new IllegalArgumentException("Payout minimum is required");
        return amount.setScale(2, RoundingMode.UNNECESSARY);
    }
}
