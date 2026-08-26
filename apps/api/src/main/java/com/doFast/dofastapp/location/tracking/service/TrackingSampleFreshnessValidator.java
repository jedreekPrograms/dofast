package com.doFast.dofastapp.location.tracking.service;

import com.doFast.dofastapp.common.exception.ConflictException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;

@Component
public class TrackingSampleFreshnessValidator {

    private final Duration maxSampleAge;
    private final Duration futureTolerance;
    private final Clock clock;

    @Autowired
    public TrackingSampleFreshnessValidator(
            @Value("${dofast.tracking.max-sample-age-seconds:120}") long maxSampleAgeSeconds,
            @Value("${dofast.tracking.future-tolerance-seconds:30}") long futureToleranceSeconds
    ) {
        this(maxSampleAgeSeconds, futureToleranceSeconds, Clock.systemUTC());
    }

    TrackingSampleFreshnessValidator(long maxSampleAgeSeconds, long futureToleranceSeconds, Clock clock) {
        if (maxSampleAgeSeconds <= 0) {
            throw new IllegalArgumentException("Tracking max sample age must be positive");
        }
        if (futureToleranceSeconds < 0) {
            throw new IllegalArgumentException("Tracking future tolerance cannot be negative");
        }
        this.maxSampleAge = Duration.ofSeconds(maxSampleAgeSeconds);
        this.futureTolerance = Duration.ofSeconds(futureToleranceSeconds);
        this.clock = clock;
    }

    public void validate(Instant capturedAt) {
        Instant now = clock.instant();
        if (capturedAt.isBefore(now.minus(maxSampleAge))) {
            throw new ConflictException("Aktualizacja lokalizacji jest zbyt stara");
        }
        if (capturedAt.isAfter(now.plus(futureTolerance))) {
            throw new ConflictException("Czas aktualizacji lokalizacji jest nieprawidłowy");
        }
    }
}
