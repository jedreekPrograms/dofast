package com.doFast.dofastapp.payout.entity;

import com.doFast.dofastapp.payout.provider.PayoutProviderSettlementOutcome;
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
import jakarta.persistence.UniqueConstraint;

import java.time.LocalDateTime;

@Entity
@Table(
        name = "payout_provider_events",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_payout_provider_event",
                columnNames = {"provider_code", "provider_event_id"}
        )
)
public class PayoutProviderEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "payout_id", nullable = false)
    private PayoutRequest payout;

    @Column(name = "provider_code", nullable = false, length = 32)
    private String providerCode;

    @Column(name = "provider_event_id", nullable = false, length = 255)
    private String providerEventId;

    @Column(name = "provider_reference", nullable = false, length = 255)
    private String providerReference;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private PayoutProviderSettlementOutcome outcome;

    @Column(name = "failure_code", length = 64)
    private String failureCode;

    @Column(name = "received_at", nullable = false)
    private LocalDateTime receivedAt;

    public PayoutProviderEvent() {}

    public PayoutProviderEvent(
            PayoutRequest payout,
            String providerCode,
            String providerEventId,
            String providerReference,
            PayoutProviderSettlementOutcome outcome,
            String failureCode,
            LocalDateTime receivedAt
    ) {
        this.payout = payout;
        this.providerCode = providerCode;
        this.providerEventId = providerEventId;
        this.providerReference = providerReference;
        this.outcome = outcome;
        this.failureCode = failureCode;
        this.receivedAt = receivedAt;
    }

    public Long getId() { return id; }
    public PayoutRequest getPayout() { return payout; }
    public String getProviderCode() { return providerCode; }
    public String getProviderEventId() { return providerEventId; }
    public String getProviderReference() { return providerReference; }
    public PayoutProviderSettlementOutcome getOutcome() { return outcome; }
    public String getFailureCode() { return failureCode; }
    public LocalDateTime getReceivedAt() { return receivedAt; }
}
