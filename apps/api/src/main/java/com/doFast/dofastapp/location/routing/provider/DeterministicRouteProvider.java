package com.doFast.dofastapp.location.routing.provider;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(prefix = "dofast.routing", name = "provider", havingValue = "deterministic", matchIfMissing = true)
public class DeterministicRouteProvider implements RouteProvider {

    private static final double EARTH_RADIUS_METERS = 6_371_000.0;

    @Override
    public RouteProviderResult estimate(RouteCoordinate origin, RouteCoordinate destination, RouteTravelMode travelMode) {
        double straightLine = haversineMeters(origin, destination);
        ModeProfile profile = profile(travelMode);
        int distance = Math.max(1, (int) Math.ceil(straightLine * profile.distanceFactor()));
        int duration = Math.max(60, (int) Math.ceil(distance / profile.speedMetersPerSecond()));
        return new RouteProviderResult(distance, duration, null, "DETERMINISTIC_DEV");
    }

    private ModeProfile profile(RouteTravelMode travelMode) {
        return switch (travelMode) {
            case DRIVE -> new ModeProfile(1.25, 35_000.0 / 3_600.0);
            case BICYCLE -> new ModeProfile(1.15, 16_000.0 / 3_600.0);
            case WALK -> new ModeProfile(1.10, 4_800.0 / 3_600.0);
        };
    }

    private double haversineMeters(RouteCoordinate first, RouteCoordinate second) {
        double lat1 = Math.toRadians(first.latitude());
        double lat2 = Math.toRadians(second.latitude());
        double deltaLat = Math.toRadians(second.latitude() - first.latitude());
        double deltaLon = Math.toRadians(second.longitude() - first.longitude());
        double a = Math.sin(deltaLat / 2) * Math.sin(deltaLat / 2)
                + Math.cos(lat1) * Math.cos(lat2)
                * Math.sin(deltaLon / 2) * Math.sin(deltaLon / 2);
        double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
        return EARTH_RADIUS_METERS * c;
    }

    private record ModeProfile(double distanceFactor, double speedMetersPerSecond) {}
}
