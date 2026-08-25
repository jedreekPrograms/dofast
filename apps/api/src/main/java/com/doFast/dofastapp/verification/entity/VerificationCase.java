package com.doFast.dofastapp.verification.entity;

import com.doFast.dofastapp.user.entity.User;
import com.doFast.dofastapp.verification.enums.VerificationStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
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
        name = "identity_verifications",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_identity_verifications_user",
                columnNames = "user_id"
        ),
        indexes = {
                @Index(name = "idx_identity_verifications_status_requested", columnList = "status, requested_at"),
                @Index(name = "idx_identity_verifications_reviewed_by", columnList = "reviewed_by_id")
        }
)
public class VerificationCase {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Version
    @Column(nullable = false)
    private Integer version = 0;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private VerificationStatus status;

    @Column(nullable = false, length = 32)
    private String provider;

    @Column(name = "provider_reference", length = 255)
    private String providerReference;

    @Column(name = "requested_at", nullable = false)
    private LocalDateTime requestedAt;

    @Column(name = "reviewed_at")
    private LocalDateTime reviewedAt;

    @Column(name = "verified_at")
    private LocalDateTime verifiedAt;

    @Column(name = "revoked_at")
    private LocalDateTime revokedAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "reviewed_by_id")
    private User reviewedBy;

    @Column(name = "decision_reason", length = 500)
    private String decisionReason;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    public VerificationCase() {}

    public void initialize(User user, String provider, String providerReference, LocalDateTime now) {
        this.user = user;
        this.provider = provider;
        this.providerReference = providerReference;
        this.status = VerificationStatus.PENDING;
        this.requestedAt = now;
        this.reviewedAt = null;
        this.verifiedAt = null;
        this.revokedAt = null;
        this.reviewedBy = null;
        this.decisionReason = null;
        this.updatedAt = now;
    }

    public void resubmit(String provider, String providerReference, LocalDateTime now) {
        this.provider = provider;
        this.providerReference = providerReference;
        this.status = VerificationStatus.PENDING;
        this.requestedAt = now;
        this.reviewedAt = null;
        this.verifiedAt = null;
        this.revokedAt = null;
        this.reviewedBy = null;
        this.decisionReason = null;
        this.updatedAt = now;
    }

    public void approve(User reviewer, LocalDateTime now) {
        this.status = VerificationStatus.VERIFIED;
        this.reviewedBy = reviewer;
        this.reviewedAt = now;
        this.verifiedAt = now;
        this.revokedAt = null;
        this.decisionReason = null;
        this.updatedAt = now;
    }

    public void reject(User reviewer, String reason, LocalDateTime now) {
        this.status = VerificationStatus.REJECTED;
        this.reviewedBy = reviewer;
        this.reviewedAt = now;
        this.verifiedAt = null;
        this.revokedAt = null;
        this.decisionReason = reason;
        this.updatedAt = now;
    }

    public void revoke(User reviewer, String reason, LocalDateTime now) {
        this.status = VerificationStatus.REVOKED;
        this.reviewedBy = reviewer;
        this.reviewedAt = now;
        this.revokedAt = now;
        this.decisionReason = reason;
        this.updatedAt = now;
    }

    public Long getId() { return id; }
    public Integer getVersion() { return version; }
    public User getUser() { return user; }
    public VerificationStatus getStatus() { return status; }
    public String getProvider() { return provider; }
    public String getProviderReference() { return providerReference; }
    public LocalDateTime getRequestedAt() { return requestedAt; }
    public LocalDateTime getReviewedAt() { return reviewedAt; }
    public LocalDateTime getVerifiedAt() { return verifiedAt; }
    public LocalDateTime getRevokedAt() { return revokedAt; }
    public User getReviewedBy() { return reviewedBy; }
    public String getDecisionReason() { return decisionReason; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
}
