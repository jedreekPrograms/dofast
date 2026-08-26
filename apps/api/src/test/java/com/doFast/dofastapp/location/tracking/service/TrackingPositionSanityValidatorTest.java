package com.doFast.dofastapp.location.tracking.service;

import com.doFast.dofastapp.common.exception.ConflictException;
import com.doFast.dofastapp.location.service.GeoPointFactory;
import com.doFast.dofastapp.location.tracking.dto.LiveLocationUpdateRequest;
import org.junit.jupiter.api.Test;
import org.locationtech.jts.geom.Point;

import java.math.BigDecimal;
import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

class TrackingPositionSanityValidatorTest {

    private final TrackingPositionSanityValidator validator = new TrackingPositionSanityValidator(80.0, 150.0);

    @Test
    void acceptsFirstPositionWithUsableAccuracy() {
        LiveLocationUpdateRequest request = request(51.1079, 17.0385, 8.0, Instant.parse("2026-08-26T03:00:00Z"));

        assertDoesNotThrow(() -> validator.validate(null, null, null, request));
    }

    @Test
    void rejectsPositionWithoutReportedAccuracy() {
        LiveLocationUpdateRequest request = request(51.1079, 17.0385, null, Instant.parse("2026-08-26T03:00:00Z"));

        assertThrows(ConflictException.class, () -> validator.validate(null, null, null, request));
    }

    @Test
    void rejectsPositionWithAccuracyOutsideConfiguredQualityGate() {
        LiveLocationUpdateRequest request = request(51.1079, 17.0385, 151.0, Instant.parse("2026-08-26T03:00:00Z"));

        assertThrows(ConflictException.class, () -> validator.validate(null, null, null, request));
    }

    @Test
    void acceptsPositionAtConfiguredAccuracyBoundary() {
        LiveLocationUpdateRequest request = request(51.1079, 17.0385, 150.0, Instant.parse("2026-08-26T03:00:00Z"));

        assertDoesNotThrow(() -> validator.validate(null, null, null, request));
    }

    @Test
    void acceptsPlausibleMovement() {
        Point previous = point(51.1079, 17.0385);
        Instant previousCapturedAt = Instant.parse("2026-08-26T03:00:00Z");
        LiveLocationUpdateRequest request = request(51.1088, 17.0400, 7.0, previousCapturedAt.plusSeconds(20));

        assertDoesNotThrow(() -> validator.validate(previous, 6.0, previousCapturedAt, request));
    }

    @Test
    void rejectsImplausibleJump() {
        Point previous = point(51.1079, 17.0385);
        Instant previousCapturedAt = Instant.parse("2026-08-26T03:00:00Z");
        LiveLocationUpdateRequest request = request(52.2297, 21.0122, 5.0, previousCapturedAt.plusSeconds(30));

        assertThrows(
                ConflictException.class,
                () -> validator.validate(previous, 5.0, previousCapturedAt, request)
        );
    }

    @Test
    void accountsForReportedGpsAccuracyBeforeRejectingMovement() {
        Point previous = point(51.1079, 17.0385);
        Instant previousCapturedAt = Instant.parse("2026-08-26T03:00:00Z");
        LiveLocationUpdateRequest request = request(51.1090, 17.0385, 80.0, previousCapturedAt.plusSeconds(1));

        assertDoesNotThrow(() -> validator.validate(previous, 80.0, previousCapturedAt, request));
    }

    @Test
    void rejectsNonPositiveAccuracyConfiguration() {
        assertThrows(IllegalArgumentException.class, () -> new TrackingPositionSanityValidator(80.0, 0.0));
    }

    private LiveLocationUpdateRequest request(double latitude, double longitude, Double accuracy, Instant capturedAt) {
        return new LiveLocationUpdateRequest(
                BigDecimal.valueOf(latitude),
                BigDecimal.valueOf(longitude),
                accuracy,
                null,
                null,
                capturedAt
        );
    }

    private Point point(double latitude, double longitude) {
        return GeoPointFactory.from(BigDecimal.valueOf(latitude), BigDecimal.valueOf(longitude));
    }
}
