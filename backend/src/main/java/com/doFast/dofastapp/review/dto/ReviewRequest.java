package com.doFast.dofastapp.review.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

public class ReviewRequest {

    @NotNull
    private Long jobId;

    @Min(1)
    @Max(5)
    private int rating;

    private String comment;

    public Long getJobId() { return jobId; }
    public int getRating() { return rating; }
    public String getComment() { return comment; }

    public void setJobId(Long jobId) { this.jobId = jobId; }
    public void setRating(int rating) { this.rating = rating; }
    public void setComment(String comment) { this.comment = comment; }
}
