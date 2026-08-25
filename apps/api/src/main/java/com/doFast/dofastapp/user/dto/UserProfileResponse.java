package com.doFast.dofastapp.user.dto;

public record UserProfileResponse(
        Long userId,
        String username,
        Double averageRating,
        long reviewsCount,
        long completedJobsAsRequester,
        long completedJobsAsWorker,
        long completedJobsTotal
) {}
