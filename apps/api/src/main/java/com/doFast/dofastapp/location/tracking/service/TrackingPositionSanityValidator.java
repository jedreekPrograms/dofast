package com.doFast.dofastapp.location.tracking.service;

import com.doFast.dofastapp.common.exception.ConflictException;
import com.doFast.dofastapp.location.tracking.dto.LiveLocationUpdateRequest;
import org.locationtech.jts.geom.Point;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;

@Component
public class TrackingPositionSanityValidator {

    private static final double EARTH_RADIUS_METERS = 6_371_000.0;

    private final double maxImpliedSpeedMetersPerSecond;

    public TrackingPositionSanityValidator(
            @Value("${dofast.tracking.max-implied-speed-meters-per-second:80}")
            double maxImpliedSpeedMetersPerSecond
    ) {
        if (maxImpliedSpeedMetersPerSecond <= 0) {
            throw new IllegalArgumentException("Tracking max implied speed must be positive");
        }
        this.maxImpliedSpeedMetersPerSecond = maxImpliedSpeedMetersPerSecond;
    }

    public void validate(
            Point previousLocation,
            Double previousAccuracyMeters,
            Instant previousCapturedAt,
            LiveLocationUpdateRequest request
    ) {
        if (previousLocation == null || previousCapturedAt == null) {
            return;
        }

        long elapsedMillis = Duration.between(previousCapturedAt, request.capturedAt()).toMillis();
        if (elapsedMillis <= 0) {
            return;
        }

        double distanceMeters = haversineMeters(
                previousLocation.getY(),
                previousLocation.getX(),
                request.latitude().doubleValue(),
                request.longitude().doubleValue()
        );
        double accuracyAllowanceMeters = nonNegative(previousAccuracyMeters) + nonNegative(request.accuracyMeters());
        double effectiveDistanceMeters = Math.max(0.0, distanceMeters - accuracyAllowanceMeters);
        double elapsedSeconds = elapsedMillis / 1000.0;
        double impliedSpeedMetersPerSecond = effectiveDistanceMeters / elapsedSeconds;

        if (impliedSpeedMetersPerSecond > maxImpliedSpeedMetersPerSecond) {
            throw new ConflictException("Aktualizacja lokalizacji wskazuje nierealny skok pozycji");
        }
    }

    private double nonNegative(Double value) {
        return value == null ? 0.0 : Math.max(0.0, value);
    }

    private double haversineMeters(double latitude1, double longitude1, double latitude2, double longitude2) {
        double lat1 = Math.toRadians(latitude1);
        double lat2 = Math.toRadians(latitude2);
        double deltaLat = Math.toRadians(latitude2 - latitude1);
        double deltaLon = Math.toRadians(longitude2 - longitude1);
        double a = Math.sin(deltaLat / 2) * Math.sin(deltaLat / 2)
                + Math.cos(lat1) * Math.cos(lat2)
                * Math.sin(deltaLon / 2) * Math.sin(deltaLon / 2);
        return EARTH_RADIUS_METERS * 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
    }
}
