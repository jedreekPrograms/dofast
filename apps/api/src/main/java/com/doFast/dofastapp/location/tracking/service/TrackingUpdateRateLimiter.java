package com.doFast.dofastapp.location.tracking.service;

import com.doFast.dofastapp.common.exception.ConflictException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;

@Component
public class TrackingUpdateRateLimiter {

    private final long minUpdateIntervalMillis;

    public TrackingUpdateRateLimiter(
            @Value("${dofast.tracking.min-update-interval-millis:1000}") long minUpdateIntervalMillis
    ) {
        if (minUpdateIntervalMillis < 0) {
            throw new IllegalArgumentException("Minimalny interwał aktualizacji trackingu nie może być ujemny");
        }
        this.minUpdateIntervalMillis = minUpdateIntervalMillis;
    }

    public void validate(Instant previousReceivedAt, Instant now) {
        if (previousReceivedAt == null || minUpdateIntervalMillis == 0) {
            return;
        }

        long elapsedMillis = Duration.between(previousReceivedAt, now).toMillis();
        if (elapsedMillis < minUpdateIntervalMillis) {
            throw new ConflictException("Aktualizacje lokalizacji są wysyłane zbyt często");
        }
    }
}
