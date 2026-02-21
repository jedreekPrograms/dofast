package com.doFast.dofastapp.review.dto;

public class ReviewResponse {

    private int rating;
    private String comment;

    public ReviewResponse(int rating, String comment) {
        this.rating = rating;
        this.comment = comment;
    }

    public int getRating() { return rating; }
    public String getComment() { return comment; }
}
