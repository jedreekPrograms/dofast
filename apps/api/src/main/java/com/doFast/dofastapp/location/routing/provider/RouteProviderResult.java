package com.doFast.dofastapp.location.routing.provider;

public record RouteProviderResult(
        int distanceMeters,
        int durationSeconds,
        String encodedPolyline,
        String provider
) {}
