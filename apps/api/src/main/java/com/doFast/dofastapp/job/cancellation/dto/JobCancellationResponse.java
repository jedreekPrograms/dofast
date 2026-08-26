package com.doFast.dofastapp.job.cancellation.dto;

import com.doFast.dofastapp.job.cancellation.enums.JobCancellationStatus;

import java.time.LocalDateTime;

public record JobCancellationResponse(
        Long id,
        Long jobId,
        Long requestedById,
        Long counterpartyId,
        String reason,
        JobCancellationStatus status,
        LocalDateTime requestedAt,
        LocalDateTime resolvedAt,
        Long resolvedById
) {}
