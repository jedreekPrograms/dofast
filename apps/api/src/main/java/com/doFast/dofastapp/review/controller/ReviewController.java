package com.doFast.dofastapp.review.controller;

import com.doFast.dofastapp.common.dto.PageResponse;
import com.doFast.dofastapp.review.dto.ReviewEligibilityResponse;
import com.doFast.dofastapp.review.dto.ReviewRequest;
import com.doFast.dofastapp.review.dto.ReviewResponse;
import com.doFast.dofastapp.review.service.ReviewService;
import com.doFast.dofastapp.user.entity.User;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/reviews")
@Validated
public class ReviewController {

    private final ReviewService reviewService;

    public ReviewController(ReviewService reviewService) {
        this.reviewService = reviewService;
    }

    @PostMapping
    public ReviewResponse addReview(
            @RequestBody @Valid ReviewRequest request,
            @AuthenticationPrincipal User user
    ) {
        return reviewService.addReview(request, user);
    }

    @GetMapping("/jobs/{jobId}/eligibility")
    public ReviewEligibilityResponse eligibility(
            @PathVariable Long jobId,
            @AuthenticationPrincipal User user
    ) {
        return reviewService.getEligibility(jobId, user);
    }

    @GetMapping("/users/{userId}")
    public PageResponse<ReviewResponse> receivedReviews(
            @PathVariable Long userId,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "10") @Min(1) @Max(50) int size
    ) {
        return reviewService.getReceivedReviews(userId, page, size);
    }
}
