package com.doFast.dofastapp.job.dto;

import com.doFast.dofastapp.common.enums.JobStatus;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record NearbyJobResponse(
        Long id,
        String title,
        String description,
        BigDecimal price,
        JobStatus status,
        String locationLabel,
        String destinationLabel,
        Integer routeDistanceMeters,
        Integer routeDurationSeconds,
        long distanceMeters,
        LocalDateTime createdAt
) {}
