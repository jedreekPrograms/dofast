package com.doFast.dofastapp.location.tracking.dto;

import java.time.Instant;

public record LiveTrackingPointResponse(
        double latitude,
        double longitude,
        Double accuracyMeters,
        Double headingDegrees,
        Double speedMetersPerSecond,
        Instant capturedAt
) {}
