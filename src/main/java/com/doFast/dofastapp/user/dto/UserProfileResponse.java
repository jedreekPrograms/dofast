package com.doFast.dofastapp.user.dto;

public class UserProfileResponse {

    private Long userId;
    private String username;
    private Double averageRating;
    private Long reviewsCount;

    public UserProfileResponse(Long userId,
                               String username,
                               Double averageRating,
                               Long reviewsCount) {
        this.userId = userId;
        this.username = username;
        this.averageRating = averageRating;
        this.reviewsCount = reviewsCount;
    }

    public Long getUserId() { return userId; }
    public String getUsername() { return username; }
    public Double getAverageRating() { return averageRating; }
    public Long getReviewsCount() { return reviewsCount; }
}
