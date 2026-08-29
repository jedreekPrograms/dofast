package com.doFast.dofastapp.payment.refund.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.LocalDateTime;

@Entity
@Table(name = "stripe_refund_events")
public class StripeRefundEvent {

    @Id
    @Column(name = "stripe_event_id", nullable = false, length = 255)
    private String stripeEventId;

    @Column(name = "refund_request_id", nullable = false)
    private Long refundRequestId;

    @Column(name = "stripe_refund_id", nullable = false, length = 255)
    private String stripeRefundId;

    @Column(name = "event_type", nullable = false, length = 64)
    private String eventType;

    @Column(name = "provider_created_at")
    private Long providerCreatedAt;

    @Column(name = "processed_at", nullable = false)
    private LocalDateTime processedAt;

    public StripeRefundEvent() {}

    public StripeRefundEvent(
            String stripeEventId,
            Long refundRequestId,
            String stripeRefundId,
            String eventType,
            Long providerCreatedAt,
            LocalDateTime processedAt
    ) {
        this.stripeEventId = stripeEventId;
        this.refundRequestId = refundRequestId;
        this.stripeRefundId = stripeRefundId;
        this.eventType = eventType;
        this.providerCreatedAt = providerCreatedAt;
        this.processedAt = processedAt;
    }

    public String getStripeEventId() { return stripeEventId; }
    public Long getRefundRequestId() { return refundRequestId; }
    public String getStripeRefundId() { return stripeRefundId; }
    public String getEventType() { return eventType; }
    public Long getProviderCreatedAt() { return providerCreatedAt; }
    public LocalDateTime getProcessedAt() { return processedAt; }
}
