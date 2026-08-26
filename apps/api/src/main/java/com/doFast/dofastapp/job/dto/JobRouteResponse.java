package com.doFast.dofastapp.job.dto;

import java.time.LocalDateTime;
import java.util.List;

public record JobRouteResponse(
        JobRoutePointResponse origin,
        List<JobRoutePointResponse> stops,
        JobRoutePointResponse destination,
        Integer distanceMeters,
        Integer durationSeconds,
        String encodedPolyline,
        String provider,
        LocalDateTime computedAt
) {}
