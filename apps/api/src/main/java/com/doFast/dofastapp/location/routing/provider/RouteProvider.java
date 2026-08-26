package com.doFast.dofastapp.location.routing.provider;

public interface RouteProvider {

    default RouteProviderResult estimate(RouteCoordinate origin, RouteCoordinate destination) {
        return estimate(origin, destination, RouteTravelMode.DRIVE);
    }

    RouteProviderResult estimate(RouteCoordinate origin, RouteCoordinate destination, RouteTravelMode travelMode);
}
