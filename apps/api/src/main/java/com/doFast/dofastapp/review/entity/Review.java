package com.doFast.dofastapp.review.entity;

import com.doFast.dofastapp.job.entity.Job;
import com.doFast.dofastapp.user.entity.User;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import java.time.LocalDateTime;

@Entity
@Table(
        name = "reviews",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_reviews_job_reviewer",
                columnNames = {"job_id", "reviewer_id"}
        ),
        indexes = {
                @Index(name = "idx_reviews_reviewed", columnList = "reviewed_id"),
                @Index(name = "idx_reviews_reviewed_created", columnList = "reviewed_id,created_at"),
                @Index(name = "idx_reviews_reviewer_created", columnList = "reviewer_id,created_at")
        }
)
public class Review {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false)
    @JoinColumn(name = "job_id", nullable = false)
    private Job job;

    @ManyToOne(optional = false)
    @JoinColumn(name = "reviewer_id", nullable = false)
    private User reviewer;

    @ManyToOne(optional = false)
    @JoinColumn(name = "reviewed_id", nullable = false)
    private User reviewed;

    @Column(nullable = false)
    private int rating;

    @Column(length = 2000)
    private String comment;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    public Review() {}

    public Long getId() { return id; }
    public Job getJob() { return job; }
    public User getReviewer() { return reviewer; }
    public User getReviewed() { return reviewed; }
    public int getRating() { return rating; }
    public String getComment() { return comment; }
    public LocalDateTime getCreatedAt() { return createdAt; }

    public void setJob(Job job) { this.job = job; }
    public void setReviewer(User reviewer) { this.reviewer = reviewer; }
    public void setReviewed(User reviewed) { this.reviewed = reviewed; }
    public void setRating(int rating) { this.rating = rating; }
    public void setComment(String comment) { this.comment = comment; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}
