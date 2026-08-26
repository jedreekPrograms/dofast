package com.doFast.dofastapp.job.cancellation.entity;

import com.doFast.dofastapp.job.cancellation.enums.JobCancellationStatus;
import com.doFast.dofastapp.job.entity.Job;
import com.doFast.dofastapp.user.entity.User;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

import java.time.LocalDateTime;

@Entity
@Table(name = "job_cancellation_requests")
public class JobCancellationRequest {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Version
    @Column(nullable = false)
    private int version;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "job_id", nullable = false)
    private Job job;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "requested_by_id", nullable = false)
    private User requestedBy;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "resolved_by_id")
    private User resolvedBy;

    @Column(nullable = false, length = 1000)
    private String reason;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 24)
    private JobCancellationStatus status;

    @Column(name = "requested_at", nullable = false)
    private LocalDateTime requestedAt;

    @Column(name = "resolved_at")
    private LocalDateTime resolvedAt;

    public JobCancellationRequest() {}

    public static JobCancellationRequest pending(Job job, User requestedBy, String reason, LocalDateTime at) {
        JobCancellationRequest request = new JobCancellationRequest();
        request.job = job;
        request.requestedBy = requestedBy;
        request.reason = reason;
        request.status = JobCancellationStatus.PENDING;
        request.requestedAt = at;
        return request;
    }

    public void approve(User resolvedBy, LocalDateTime at) {
        resolve(JobCancellationStatus.APPROVED, resolvedBy, at);
    }

    public void decline(User resolvedBy, LocalDateTime at) {
        resolve(JobCancellationStatus.DECLINED, resolvedBy, at);
    }

    public void withdraw(User resolvedBy, LocalDateTime at) {
        resolve(JobCancellationStatus.WITHDRAWN, resolvedBy, at);
    }

    private void resolve(JobCancellationStatus target, User resolver, LocalDateTime at) {
        if (status != JobCancellationStatus.PENDING) {
            throw new IllegalStateException("Cancellation request is already resolved");
        }
        status = target;
        resolvedBy = resolver;
        resolvedAt = at;
    }

    public Long getId() { return id; }
    public Job getJob() { return job; }
    public User getRequestedBy() { return requestedBy; }
    public User getResolvedBy() { return resolvedBy; }
    public String getReason() { return reason; }
    public JobCancellationStatus getStatus() { return status; }
    public LocalDateTime getRequestedAt() { return requestedAt; }
    public LocalDateTime getResolvedAt() { return resolvedAt; }
}
