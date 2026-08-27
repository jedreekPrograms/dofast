package com.doFast.dofastapp.job.report;

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
        name = "job_report_account_enforcements",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_job_report_account_enforcements_report",
                columnNames = "report_id"
        )
)
public class JobReportAccountEnforcement {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "report_id", nullable = false)
    private JobReport report;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "target_user_id", nullable = false)
    private User targetUser;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "moderator_id", nullable = false)
    private User moderator;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 40)
    private JobReportAccountEnforcementAction action;

    @Column(length = 1000)
    private String reason;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    protected JobReportAccountEnforcement() {}

    public JobReportAccountEnforcement(
            JobReport report,
            User targetUser,
            User moderator,
            JobReportAccountEnforcementAction action,
            String reason
    ) {
        this.report = report;
        this.targetUser = targetUser;
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
    public User getTargetUser() { return targetUser; }
    public User getModerator() { return moderator; }
    public JobReportAccountEnforcementAction getAction() { return action; }
    public String getReason() { return reason; }
    public LocalDateTime getCreatedAt() { return createdAt; }
}
