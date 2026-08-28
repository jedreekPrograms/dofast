package com.doFast.dofastapp.user.dto;

import java.time.LocalDateTime;

public record UserProfileResponse(
        Long userId,
        String username,
        String bio,
        String publicLocation,
        LocalDateTime memberSince,
        Double averageRating,
        long reviewsCount,
        long completedJobsAsRequester,
        long completedJobsAsWorker,
        long completedJobsTotal,
        boolean identityVerified
) {}
