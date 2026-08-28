package com.doFast.dofastapp.job.service;

import com.doFast.dofastapp.job.category.JobCategory;
import com.doFast.dofastapp.job.dto.JobResponse;
import com.doFast.dofastapp.job.entity.Job;

public final class JobResponseMapper {

    private JobResponseMapper() {}

    public static JobResponse toResponse(Job job) {
        JobCategory category = job.getCategory();
        return new JobResponse(
                job.getId(),
                job.getTitle(),
                job.getDescription(),
                job.getPrice(),
                job.getAssignmentMode(),
                job.isPriceNegotiationEnabled(),
                job.getStatus(),
                category != null ? category.getId() : null,
                category != null ? category.getSlug() : null,
                category != null ? category.getName() : null,
                category != null ? category.getFulfillmentMode() : null,
                job.getLocationLabel(),
                job.getDestinationLabel(),
                job.getRouteDistanceMeters(),
                job.getRouteDurationSeconds(),
                job.getCreatedBy().getId(),
                job.getTakenBy() != null ? job.getTakenBy().getId() : null,
                job.getCreatedAt(),
                job.getUpdatedAt(),
                job.getTakenAt(),
                job.getCompletionRequestedAt(),
                job.getCompletedAt(),
                job.getCancelledAt()
        );
    }
}
