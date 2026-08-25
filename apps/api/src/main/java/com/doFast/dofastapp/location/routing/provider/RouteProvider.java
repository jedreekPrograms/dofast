package com.doFast.dofastapp.location.routing.provider;

public interface RouteProvider {
    RouteProviderResult estimate(RouteCoordinate origin, RouteCoordinate destination);
}
