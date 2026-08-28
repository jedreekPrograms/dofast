package com.doFast.dofastapp.location.tracking.service;

import com.doFast.dofastapp.common.exception.ConflictException;
import org.junit.jupiter.api.Test;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.Point;
import org.locationtech.jts.geom.PrecisionModel;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

class TrackingCheckpointProximityValidatorTest {

    private static final GeometryFactory GEOMETRY = new GeometryFactory(new PrecisionModel(), 4326);
    private final TrackingCheckpointProximityValidator validator = new TrackingCheckpointProximityValidator(100, 20);

    @Test
    void acceptsFreshPositionInsideArrivalRadius() {
        Instant now = Instant.parse("2026-08-28T09:00:00Z");

        assertDoesNotThrow(() -> validator.validate(
                point(17.0385, 51.1079),
                8.0,
                now.minusSeconds(5),
                point(17.0390, 51.1080),
                now
        ));
    }

    @Test
    void rejectsPositionFarFromCheckpoint() {
        Instant now = Instant.parse("2026-08-28T09:00:00Z");

        assertThrows(ConflictException.class, () -> validator.validate(
                point(17.0385, 51.1079),
                10.0,
                now.minusSeconds(5),
                point(17.0585, 51.1079),
                now
        ));
    }

    @Test
    void rejectsStalePositionEvenWhenNearCheckpoint() {
        Instant now = Instant.parse("2026-08-28T09:00:00Z");

        assertThrows(ConflictException.class, () -> validator.validate(
                point(17.0385, 51.1079),
                5.0,
                now.minusSeconds(21),
                point(17.0386, 51.1079),
                now
        ));
    }

    @Test
    void accountsForReportedGpsAccuracyAtBoundary() {
        Instant now = Instant.parse("2026-08-28T09:00:00Z");

        assertDoesNotThrow(() -> validator.validate(
                point(17.0385, 51.1079),
                60.0,
                now.minusSeconds(2),
                point(17.0405, 51.1079),
                now
        ));
    }

    private Point point(double longitude, double latitude) {
        return GEOMETRY.createPoint(new Coordinate(longitude, latitude));
    }
}
