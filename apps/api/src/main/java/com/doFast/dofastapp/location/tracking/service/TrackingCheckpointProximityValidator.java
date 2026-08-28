package com.doFast.dofastapp.location.tracking.service;

import com.doFast.dofastapp.common.exception.ConflictException;
import org.locationtech.jts.geom.Point;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;

@Component
public class TrackingCheckpointProximityValidator {

    private static final double EARTH_RADIUS_METERS = 6_371_000.0;

    private final double arrivalRadiusMeters;
    private final long staleAfterSeconds;

    public TrackingCheckpointProximityValidator(
            @Value("${dofast.tracking.checkpoint-arrival-radius-meters:100}") double arrivalRadiusMeters,
            @Value("${dofast.tracking.stale-after-seconds:20}") long staleAfterSeconds
    ) {
        if (arrivalRadiusMeters <= 0) {
            throw new IllegalArgumentException("Checkpoint arrival radius must be positive");
        }
        if (staleAfterSeconds <= 0) {
            throw new IllegalArgumentException("Tracking stale threshold must be positive");
        }
        this.arrivalRadiusMeters = arrivalRadiusMeters;
        this.staleAfterSeconds = staleAfterSeconds;
    }

    public void validate(
            Point currentLocation,
            Double accuracyMeters,
            Instant receivedAt,
            Point targetLocation,
            Instant now
    ) {
        if (currentLocation == null || receivedAt == null) {
            throw new ConflictException("Najpierw udostępnij aktualną lokalizację przy punkcie trasy");
        }
        if (Duration.between(receivedAt, now).getSeconds() > staleAfterSeconds) {
            throw new ConflictException("Pozycja GPS jest zbyt stara, aby potwierdzić punkt trasy");
        }
        if (targetLocation == null) {
            throw new ConflictException("Punkt trasy nie ma poprawnej lokalizacji");
        }

        double rawDistance = haversineMeters(currentLocation, targetLocation);
        double accuracyAllowance = accuracyMeters == null ? 0.0 : Math.max(0.0, accuracyMeters);
        double effectiveDistance = Math.max(0.0, rawDistance - accuracyAllowance);
        if (effectiveDistance > arrivalRadiusMeters) {
            throw new ConflictException("Podejdź bliżej punktu trasy, aby go potwierdzić");
        }
    }

    private double haversineMeters(Point first, Point second) {
        double lat1 = Math.toRadians(first.getY());
        double lat2 = Math.toRadians(second.getY());
        double deltaLat = Math.toRadians(second.getY() - first.getY());
        double deltaLon = Math.toRadians(second.getX() - first.getX());
        double a = Math.sin(deltaLat / 2) * Math.sin(deltaLat / 2)
                + Math.cos(lat1) * Math.cos(lat2)
                * Math.sin(deltaLon / 2) * Math.sin(deltaLon / 2);
        return EARTH_RADIUS_METERS * 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
    }
}
