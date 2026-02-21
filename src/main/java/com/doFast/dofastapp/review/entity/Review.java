package com.doFast.dofastapp.review.entity;

import com.doFast.dofastapp.job.entity.Job;
import com.doFast.dofastapp.user.entity.User;
import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;

@Entity
public class Review {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne
    private Job job;

    @ManyToOne
    private User reviewer;

    @ManyToOne
    private User reviewed;

    private int rating;

    private String comment;

    public Review() {}

    public Long getId() { return id; }
    public Job getJob() { return job; }
    public User getReviewer() { return reviewer; }
    public User getReviewed() { return reviewed; }
    public int getRating() { return rating; }
    public String getComment() { return comment; }

    public void setJob(Job job) { this.job = job; }
    public void setReviewer(User reviewer) { this.reviewer = reviewer; }
    public void setReviewed(User reviewed) { this.reviewed = reviewed; }
    public void setRating(int rating) { this.rating = rating; }
    public void setComment(String comment) { this.comment = comment; }
}
