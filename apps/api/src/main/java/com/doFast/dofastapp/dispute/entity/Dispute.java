package com.doFast.dofastapp.dispute.entity;

import com.doFast.dofastapp.common.enums.JobStatus;
import com.doFast.dofastapp.dispute.enums.DisputeReason;
import com.doFast.dofastapp.dispute.enums.DisputeResolution;
import com.doFast.dofastapp.dispute.enums.DisputeStatus;
import com.doFast.dofastapp.job.entity.Job;
import com.doFast.dofastapp.user.entity.User;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import jakarta.persistence.Version;

import java.time.LocalDateTime;

@Entity
@Table(
        name = "disputes",
        uniqueConstraints = @UniqueConstraint(name = "uk_disputes_job", columnNames = "job_id"),
        indexes = {
                @Index(name = "idx_disputes_status_opened", columnList = "status,opened_at"),
                @Index(name = "idx_disputes_opened_by", columnList = "opened_by_id"),
                @Index(name = "idx_disputes_assigned_admin", columnList = "assigned_admin_id")
        }
)
public class Dispute {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Version
    @Column(nullable = false)
    private int version;

    @OneToOne(optional = false)
    @JoinColumn(name = "job_id", nullable = false)
    private Job job;

    @ManyToOne(optional = false)
    @JoinColumn(name = "opened_by_id", nullable = false)
    private User openedBy;

    @ManyToOne
    @JoinColumn(name = "assigned_admin_id")
    private User assignedAdmin;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private DisputeReason reason;

    @Column(nullable = false, length = 4000)
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private DisputeStatus status;

    @Enumerated(EnumType.STRING)
    @Column(name = "previous_job_status", nullable = false, length = 32)
    private JobStatus previousJobStatus;

    @Enumerated(EnumType.STRING)
    @Column(length = 32)
    private DisputeResolution resolution;

    @Column(name = "admin_note", length = 4000)
    private String adminNote;

    @Column(name = "opened_at", nullable = false)
    private LocalDateTime openedAt;

    @Column(name = "review_started_at")
    private LocalDateTime reviewStartedAt;

    @Column(name = "resolved_at")
    private LocalDateTime resolvedAt;

    @Column(name = "cancelled_at")
    private LocalDateTime cancelledAt;

    public Dispute() {}

    public void startReview(User admin, LocalDateTime at) {
        this.assignedAdmin = admin;
        this.status = DisputeStatus.UNDER_REVIEW;
        if (this.reviewStartedAt == null) {
            this.reviewStartedAt = at;
        }
    }

    public void resolve(User admin, DisputeResolution resolution, String note, LocalDateTime at) {
        this.assignedAdmin = admin;
        this.status = DisputeStatus.RESOLVED;
        this.resolution = resolution;
        this.adminNote = note;
        this.resolvedAt = at;
        if (this.reviewStartedAt == null) {
            this.reviewStartedAt = at;
        }
    }

    public void cancel(LocalDateTime at) {
        this.status = DisputeStatus.CANCELLED;
        this.cancelledAt = at;
    }

    public Long getId() { return id; }
    public Job getJob() { return job; }
    public User getOpenedBy() { return openedBy; }
    public User getAssignedAdmin() { return assignedAdmin; }
    public DisputeReason getReason() { return reason; }
    public String getDescription() { return description; }
    public DisputeStatus getStatus() { return status; }
    public JobStatus getPreviousJobStatus() { return previousJobStatus; }
    public DisputeResolution getResolution() { return resolution; }
    public String getAdminNote() { return adminNote; }
    public LocalDateTime getOpenedAt() { return openedAt; }
    public LocalDateTime getReviewStartedAt() { return reviewStartedAt; }
    public LocalDateTime getResolvedAt() { return resolvedAt; }
    public LocalDateTime getCancelledAt() { return cancelledAt; }

    public void setJob(Job job) { this.job = job; }
    public void setOpenedBy(User openedBy) { this.openedBy = openedBy; }
    public void setReason(DisputeReason reason) { this.reason = reason; }
    public void setDescription(String description) { this.description = description; }
    public void setStatus(DisputeStatus status) { this.status = status; }
    public void setPreviousJobStatus(JobStatus previousJobStatus) { this.previousJobStatus = previousJobStatus; }
    public void setOpenedAt(LocalDateTime openedAt) { this.openedAt = openedAt; }
}
