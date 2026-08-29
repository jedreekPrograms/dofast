package com.doFast.dofastapp.job.dto;

import com.doFast.dofastapp.common.enums.JobStatus;
import com.doFast.dofastapp.job.assignment.JobAssignmentMode;
import com.doFast.dofastapp.job.category.FulfillmentMode;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record JobResponse(
        Long id,
        String title,
        String description,
        BigDecimal price,
        BigDecimal expenseBudget,
        JobAssignmentMode assignmentMode,
        boolean priceNegotiationEnabled,
        JobStatus status,
        Long categoryId,
        String categorySlug,
        String categoryName,
        FulfillmentMode fulfillmentMode,
        String locationLabel,
        String destinationLabel,
        Integer routeDistanceMeters,
        Integer routeDurationSeconds,
        Long createdById,
        Long takenById,
        LocalDateTime createdAt,
        LocalDateTime updatedAt,
        LocalDateTime takenAt,
        LocalDateTime completionRequestedAt,
        LocalDateTime completedAt,
        LocalDateTime cancelledAt
) {}
