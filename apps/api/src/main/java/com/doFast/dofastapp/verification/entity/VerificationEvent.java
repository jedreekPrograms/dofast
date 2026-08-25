package com.doFast.dofastapp.verification.entity;

import com.doFast.dofastapp.user.entity.User;
import com.doFast.dofastapp.verification.enums.VerificationEventType;
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
import jakarta.persistence.Table;

import java.time.LocalDateTime;

@Entity
@Table(
        name = "identity_verification_events",
        indexes = {
                @Index(name = "idx_identity_verification_events_case_created", columnList = "verification_id, created_at"),
                @Index(name = "idx_identity_verification_events_actor", columnList = "actor_user_id")
        }
)
public class VerificationEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "verification_id", nullable = false)
    private VerificationCase verification;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "actor_user_id", nullable = false)
    private User actor;

    @Enumerated(EnumType.STRING)
    @Column(name = "event_type", nullable = false, length = 24)
    private VerificationEventType eventType;

    @Enumerated(EnumType.STRING)
    @Column(name = "from_status", length = 20)
    private VerificationStatus fromStatus;

    @Enumerated(EnumType.STRING)
    @Column(name = "to_status", nullable = false, length = 20)
    private VerificationStatus toStatus;

    @Column(length = 500)
    private String reason;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    public VerificationEvent() {}

    public VerificationEvent(
            VerificationCase verification,
            User actor,
            VerificationEventType eventType,
            VerificationStatus fromStatus,
            VerificationStatus toStatus,
            String reason,
            LocalDateTime createdAt
    ) {
        this.verification = verification;
        this.actor = actor;
        this.eventType = eventType;
        this.fromStatus = fromStatus;
        this.toStatus = toStatus;
        this.reason = reason;
        this.createdAt = createdAt;
    }

    public Long getId() { return id; }
    public VerificationCase getVerification() { return verification; }
    public User getActor() { return actor; }
    public VerificationEventType getEventType() { return eventType; }
    public VerificationStatus getFromStatus() { return fromStatus; }
    public VerificationStatus getToStatus() { return toStatus; }
    public String getReason() { return reason; }
    public LocalDateTime getCreatedAt() { return createdAt; }
}
