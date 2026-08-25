package com.doFast.dofastapp.location.routing.dto;

import java.time.LocalDateTime;
import java.util.UUID;

public record RouteQuoteResponse(
        UUID id,
        RoutePointResponse origin,
        RoutePointResponse destination,
        int distanceMeters,
        int durationSeconds,
        String encodedPolyline,
        String provider,
        LocalDateTime createdAt,
        LocalDateTime expiresAt
) {}
