package com.doFast.dofastapp.location.routing.provider;

import java.util.List;

public interface RouteProvider {

    default RouteProviderResult estimate(RouteCoordinate origin, RouteCoordinate destination) {
        return estimate(origin, List.of(), destination, RouteTravelMode.DRIVE);
    }

    default RouteProviderResult estimate(
            RouteCoordinate origin,
            RouteCoordinate destination,
            RouteTravelMode travelMode
    ) {
        return estimate(origin, List.of(), destination, travelMode);
    }

    RouteProviderResult estimate(
            RouteCoordinate origin,
            List<RouteCoordinate> intermediates,
            RouteCoordinate destination,
            RouteTravelMode travelMode
    );
}
