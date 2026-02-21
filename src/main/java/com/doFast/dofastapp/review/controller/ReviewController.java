package com.doFast.dofastapp.review.controller;

import com.doFast.dofastapp.review.dto.ReviewRequest;
import com.doFast.dofastapp.review.dto.ReviewResponse;
import com.doFast.dofastapp.review.service.ReviewService;
import com.doFast.dofastapp.user.entity.User;
import jakarta.validation.Valid;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/reviews")
public class ReviewController {

    private final ReviewService reviewService;

    public ReviewController(ReviewService reviewService) {
        this.reviewService = reviewService;
    }

    @PostMapping
    public ReviewResponse addReview(@RequestBody @Valid ReviewRequest request) {

        Authentication authentication =
                SecurityContextHolder.getContext().getAuthentication();

        User user = (User) authentication.getPrincipal();

        return reviewService.addReview(request, user);
    }
}
