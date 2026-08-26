package com.doFast.dofastapp.location.tracking.service;

import com.doFast.dofastapp.common.exception.ConflictException;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

class TrackingSampleFreshnessValidatorTest {

    private static final Instant NOW = Instant.parse("2026-08-26T10:00:00Z");
    private final Clock clock = Clock.fixed(NOW, ZoneOffset.UTC);
    private final TrackingSampleFreshnessValidator validator = new TrackingSampleFreshnessValidator(120, 30, clock);

    @Test
    void acceptsCurrentSample() {
        assertDoesNotThrow(() -> validator.validate(NOW));
    }

    @Test
    void acceptsConfiguredAgeBoundary() {
        assertDoesNotThrow(() -> validator.validate(NOW.minusSeconds(120)));
    }

    @Test
    void rejectsSampleOlderThanConfiguredWindow() {
        assertThrows(ConflictException.class, () -> validator.validate(NOW.minusSeconds(121)));
    }

    @Test
    void acceptsConfiguredFutureClockSkewBoundary() {
        assertDoesNotThrow(() -> validator.validate(NOW.plusSeconds(30)));
    }

    @Test
    void rejectsSampleTooFarInFuture() {
        assertThrows(ConflictException.class, () -> validator.validate(NOW.plusSeconds(31)));
    }

    @Test
    void rejectsInvalidConfiguration() {
        assertThrows(IllegalArgumentException.class, () -> new TrackingSampleFreshnessValidator(0, 30, clock));
        assertThrows(IllegalArgumentException.class, () -> new TrackingSampleFreshnessValidator(120, -1, clock));
    }
}
