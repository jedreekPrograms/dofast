package com.doFast.dofastapp.payout.entity;

import com.doFast.dofastapp.payout.enums.PayoutEventSource;
import com.doFast.dofastapp.payout.enums.PayoutEventType;
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

import java.time.LocalDateTime;

@Entity
@Table(name = "payout_events")
public class PayoutEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "payout_id", nullable = false)
    private PayoutRequest payout;

    @Enumerated(EnumType.STRING)
    @Column(name = "event_type", nullable = false, length = 32)
    private PayoutEventType eventType;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private PayoutEventSource source;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "actor_user_id")
    private User actor;

    @Column(length = 500)
    private String note;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    public PayoutEvent() {}

    public PayoutEvent(
            PayoutRequest payout,
            PayoutEventType eventType,
            PayoutEventSource source,
            User actor,
            String note,
            LocalDateTime createdAt
    ) {
        this.payout = payout;
        this.eventType = eventType;
        this.source = source;
        this.actor = actor;
        this.note = note;
        this.createdAt = createdAt;
    }

    public Long getId() { return id; }
    public PayoutRequest getPayout() { return payout; }
    public PayoutEventType getEventType() { return eventType; }
    public PayoutEventSource getSource() { return source; }
    public User getActor() { return actor; }
    public String getNote() { return note; }
    public LocalDateTime getCreatedAt() { return createdAt; }
}
