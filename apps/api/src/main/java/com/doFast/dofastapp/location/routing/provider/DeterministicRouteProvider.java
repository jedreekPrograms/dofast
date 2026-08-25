package com.doFast.dofastapp.location.routing.provider;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(prefix = "dofast.routing", name = "provider", havingValue = "deterministic", matchIfMissing = true)
public class DeterministicRouteProvider implements RouteProvider {

    private static final double EARTH_RADIUS_METERS = 6_371_000.0;
    private static final double ROAD_FACTOR = 1.25;
    private static final double AVERAGE_URBAN_SPEED_METERS_PER_SECOND = 35_000.0 / 3_600.0;

    @Override
    public RouteProviderResult estimate(RouteCoordinate origin, RouteCoordinate destination) {
        double straightLine = haversineMeters(origin, destination);
        int distance = Math.max(1, (int) Math.ceil(straightLine * ROAD_FACTOR));
        int duration = Math.max(60, (int) Math.ceil(distance / AVERAGE_URBAN_SPEED_METERS_PER_SECOND));
        return new RouteProviderResult(distance, duration, null, "DETERMINISTIC_DEV");
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
}
