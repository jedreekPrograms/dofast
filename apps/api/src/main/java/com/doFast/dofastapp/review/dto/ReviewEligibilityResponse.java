package com.doFast.dofastapp.review.dto;

public record ReviewEligibilityResponse(
        Long jobId,
        boolean eligible,
        boolean alreadyReviewed,
        Long counterpartId,
        String counterpartNickname
) {}
