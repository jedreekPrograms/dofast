package com.doFast.dofastapp.location.tracking.dto;

import com.doFast.dofastapp.location.tracking.enums.TrackingPhase;

import java.time.Instant;

public record LiveTrackingResponse(
        Long jobId,
        Long workerId,
        TrackingPhase phase,
        boolean sharingActive,
        LiveTrackingPointResponse location,
        Integer remainingDistanceMeters,
        Integer remainingDurationSeconds,
        String remainingEncodedPolyline,
        String remainingProvider,
        Instant remainingComputedAt,
        Instant receivedAt,
        boolean stale
) {}
