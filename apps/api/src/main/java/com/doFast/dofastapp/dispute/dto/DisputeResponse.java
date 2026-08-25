package com.doFast.dofastapp.dispute.dto;

import com.doFast.dofastapp.common.enums.JobStatus;
import com.doFast.dofastapp.dispute.enums.DisputeReason;
import com.doFast.dofastapp.dispute.enums.DisputeResolution;
import com.doFast.dofastapp.dispute.enums.DisputeStatus;

import java.time.LocalDateTime;

public record DisputeResponse(
        Long id,
        Long jobId,
        String jobTitle,
        Long requesterId,
        Long workerId,
        Long openedById,
        Long assignedAdminId,
        DisputeReason reason,
        String description,
        DisputeStatus status,
        JobStatus previousJobStatus,
        DisputeResolution resolution,
        String adminNote,
        LocalDateTime openedAt,
        LocalDateTime reviewStartedAt,
        LocalDateTime resolvedAt,
        LocalDateTime cancelledAt
) {}
