package com.doFast.dofastapp.user.dto;

import java.time.LocalDateTime;
import java.util.List;

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
        boolean identityVerified,
        List<UserServiceCategoryResponse> serviceCategories
) {}
