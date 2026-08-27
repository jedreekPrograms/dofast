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

import java.time.LocalDateTime;

@Entity
@Table(
        name = "job_report_enforcements",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_job_report_enforcements_report",
                columnNames = "report_id"
        )
)
public class JobReportEnforcement {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "report_id", nullable = false)
    private JobReport report;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "job_id", nullable = false)
    private Job job;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "moderator_id", nullable = false)
    private User moderator;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private JobReportEnforcementAction action;

    @Column(length = 1000)
    private String reason;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    protected JobReportEnforcement() {}

    public JobReportEnforcement(
            JobReport report,
            Job job,
            User moderator,
            JobReportEnforcementAction action,
            String reason
    ) {
        this.report = report;
        this.job = job;
        this.moderator = moderator;
        this.action = action;
        this.reason = reason;
    }

    @PrePersist
    void prePersist() {
        if (createdAt == null) {
            createdAt = LocalDateTime.now();
        }
    }

    public Long getId() { return id; }
    public JobReport getReport() { return report; }
    public Job getJob() { return job; }
    public User getModerator() { return moderator; }
    public JobReportEnforcementAction getAction() { return action; }
    public String getReason() { return reason; }
    public LocalDateTime getCreatedAt() { return createdAt; }
}
