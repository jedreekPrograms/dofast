package com.doFast.dofastapp.job.dto;

import com.doFast.dofastapp.common.enums.JobStatus;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record JobResponse(
        Long id,
        String title,
        String description,
        BigDecimal price,
        JobStatus status,
        Long createdById,
        Long takenById,
        LocalDateTime createdAt,
        LocalDateTime updatedAt,
        LocalDateTime takenAt,
        LocalDateTime completionRequestedAt,
        LocalDateTime completedAt,
        LocalDateTime cancelledAt
) {}
