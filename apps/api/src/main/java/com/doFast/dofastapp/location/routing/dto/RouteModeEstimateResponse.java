package com.doFast.dofastapp.location.routing.dto;

import com.doFast.dofastapp.location.routing.provider.RouteTravelMode;

public record RouteModeEstimateResponse(
        RouteTravelMode mode,
        Integer distanceMeters,
        Integer durationSeconds,
        boolean available
) {}
