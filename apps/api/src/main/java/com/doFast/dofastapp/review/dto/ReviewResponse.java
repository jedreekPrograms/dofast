package com.doFast.dofastapp.review.dto;

import java.time.LocalDateTime;

public record ReviewResponse(
        Long id,
        Long jobId,
        String jobTitle,
        Long reviewerId,
        String reviewerNickname,
        Long reviewedId,
        int rating,
        String comment,
        LocalDateTime createdAt
) {}
