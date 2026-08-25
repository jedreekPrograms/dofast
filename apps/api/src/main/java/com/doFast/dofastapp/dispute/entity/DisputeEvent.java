package com.doFast.dofastapp.dispute.entity;

import com.doFast.dofastapp.dispute.enums.DisputeEventType;
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
import jakarta.persistence.Table;

import java.time.LocalDateTime;

@Entity
@Table(
        name = "dispute_events",
        indexes = {
                @Index(name = "idx_dispute_events_dispute_created", columnList = "dispute_id,created_at"),
                @Index(name = "idx_dispute_events_actor", columnList = "actor_id")
        }
)
public class DisputeEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false)
    @JoinColumn(name = "dispute_id", nullable = false)
    private Dispute dispute;

    @ManyToOne(optional = false)
    @JoinColumn(name = "actor_id", nullable = false)
    private User actor;

    @Enumerated(EnumType.STRING)
    @Column(name = "event_type", nullable = false, length = 32)
    private DisputeEventType eventType;

    @Column(length = 4000)
    private String note;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    public DisputeEvent() {}

    public Long getId() { return id; }
    public Dispute getDispute() { return dispute; }
    public User getActor() { return actor; }
    public DisputeEventType getEventType() { return eventType; }
    public String getNote() { return note; }
    public LocalDateTime getCreatedAt() { return createdAt; }

    public void setDispute(Dispute dispute) { this.dispute = dispute; }
    public void setActor(User actor) { this.actor = actor; }
    public void setEventType(DisputeEventType eventType) { this.eventType = eventType; }
    public void setNote(String note) { this.note = note; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}
