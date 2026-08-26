package com.doFast.dofastapp.location.tracking.service;

import com.doFast.dofastapp.common.exception.ConflictException;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

class TrackingUpdateRateLimiterTest {

    private static final Instant NOW = Instant.parse("2026-08-26T05:00:00Z");

    @Test
    void acceptsFirstUpdate() {
        TrackingUpdateRateLimiter limiter = new TrackingUpdateRateLimiter(1000);

        assertDoesNotThrow(() -> limiter.validate(null, NOW));
    }

    @Test
    void rejectsUpdateBeforeMinimumInterval() {
        TrackingUpdateRateLimiter limiter = new TrackingUpdateRateLimiter(1000);

        assertThrows(
                ConflictException.class,
                () -> limiter.validate(NOW.minusMillis(999), NOW)
        );
    }

    @Test
    void acceptsUpdateAtMinimumInterval() {
        TrackingUpdateRateLimiter limiter = new TrackingUpdateRateLimiter(1000);

        assertDoesNotThrow(() -> limiter.validate(NOW.minusMillis(1000), NOW));
    }

    @Test
    void canBeDisabledForControlledEnvironments() {
        TrackingUpdateRateLimiter limiter = new TrackingUpdateRateLimiter(0);

        assertDoesNotThrow(() -> limiter.validate(NOW, NOW));
    }

    @Test
    void rejectsNegativeConfiguration() {
        assertThrows(IllegalArgumentException.class, () -> new TrackingUpdateRateLimiter(-1));
    }
}
