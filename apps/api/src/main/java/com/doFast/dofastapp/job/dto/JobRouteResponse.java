package com.doFast.dofastapp.job.dto;

import java.time.LocalDateTime;

public record JobRouteResponse(
        JobRoutePointResponse origin,
        JobRoutePointResponse destination,
        Integer distanceMeters,
        Integer durationSeconds,
        String encodedPolyline,
        String provider,
        LocalDateTime computedAt
) {}
