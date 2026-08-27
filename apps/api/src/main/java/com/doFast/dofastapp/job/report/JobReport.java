package com.doFast.dofastapp.job.report;

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
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import jakarta.persistence.Version;

import java.time.LocalDateTime;

@Entity
@Table(
        name = "job_reports",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_job_reports_reporter_job",
                columnNames = {"reporter_id", "job_id"}
        )
)
public class JobReport {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "job_id", nullable = false)
    private Job job;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "reporter_id", nullable = false)
    private User reporter;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private JobReportReason reason;

    @Column(length = 1000)
    private String details;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 24)
    private JobReportStatus status = JobReportStatus.SUBMITTED;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "reviewed_at")
    private LocalDateTime reviewedAt;

    @Column(name = "withdrawn_at")
    private LocalDateTime withdrawnAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "reviewed_by_id")
    private User reviewedBy;

    @Column(name = "moderation_note", length = 1000)
    private String moderationNote;

    @Version
    @Column(nullable = false)
    private long version;

    protected JobReport() {}

    public JobReport(Job job, User reporter, JobReportReason reason, String details) {
        this.job = job;
        this.reporter = reporter;
        this.reason = reason;
        this.details = details;
    }

    @PrePersist
    void prePersist() {
        if (createdAt == null) {
            createdAt = LocalDateTime.now();
        }
    }

    public void moderate(JobReportStatus status, User moderator, String note) {
        this.status = status;
        this.reviewedBy = moderator;
        this.moderationNote = note;
        this.reviewedAt = LocalDateTime.now();
    }

    public void withdraw() {
        this.status = JobReportStatus.WITHDRAWN;
        this.withdrawnAt = LocalDateTime.now();
    }

    public Long getId() { return id; }
    public Job getJob() { return job; }
    public User getReporter() { return reporter; }
    public JobReportReason getReason() { return reason; }
    public String getDetails() { return details; }
    public JobReportStatus getStatus() { return status; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public LocalDateTime getReviewedAt() { return reviewedAt; }
    public LocalDateTime getWithdrawnAt() { return withdrawnAt; }
    public User getReviewedBy() { return reviewedBy; }
    public String getModerationNote() { return moderationNote; }
    public long getVersion() { return version; }
}
