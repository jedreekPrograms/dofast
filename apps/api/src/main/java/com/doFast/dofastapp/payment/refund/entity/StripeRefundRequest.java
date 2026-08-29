package com.doFast.dofastapp.payment.refund.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import jakarta.persistence.Version;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(
        name = "stripe_refund_requests",
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_stripe_refund_requests_user_key", columnNames = {"user_id", "request_key"}),
                @UniqueConstraint(name = "uk_stripe_refund_requests_refund", columnNames = "stripe_refund_id")
        },
        indexes = {
                @Index(name = "idx_stripe_refund_requests_payment", columnList = "stripe_payment_intent_id,status,id"),
                @Index(name = "idx_stripe_refund_requests_user", columnList = "user_id,created_at,id")
        }
)
public class StripeRefundRequest {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Version
    @Column(nullable = false)
    private Integer version = 0;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "stripe_payment_intent_id", nullable = false, length = 255)
    private String stripePaymentIntentId;

    @Column(name = "request_key", nullable = false, length = 128)
    private String requestKey;

    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal amount;

    @Column(nullable = false, length = 3)
    private String currency;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private StripeRefundStatus status;

    @Column(name = "stripe_refund_id", length = 255)
    private String stripeRefundId;

    @Column(name = "stripe_status", length = 32)
    private String stripeStatus;

    @Column(name = "failure_reason", length = 64)
    private String failureReason;

    @Column(name = "attempt_count", nullable = false)
    private int attemptCount;

    @Column(name = "next_attempt_at")
    private LocalDateTime nextAttemptAt;

    @Column(name = "provider_event_created_at")
    private Long providerEventCreatedAt;

    @Column(name = "wallet_restored", nullable = false)
    private boolean walletRestored;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @Column(name = "submitted_at")
    private LocalDateTime submittedAt;

    @Column(name = "resolved_at")
    private LocalDateTime resolvedAt;

    public StripeRefundRequest() {}

    public static StripeRefundRequest create(
            Long userId,
            String paymentIntentId,
            String requestKey,
            BigDecimal amount,
            String currency,
            LocalDateTime now
    ) {
        StripeRefundRequest request = new StripeRefundRequest();
        request.userId = userId;
        request.stripePaymentIntentId = paymentIntentId;
        request.requestKey = requestKey;
        request.amount = amount;
        request.currency = currency;
        request.status = StripeRefundStatus.REQUESTED;
        request.attemptCount = 0;
        request.nextAttemptAt = now;
        request.walletRestored = false;
        request.createdAt = now;
        request.updatedAt = now;
        return request;
    }

    public void startDispatch(LocalDateTime now) {
        if (status != StripeRefundStatus.REQUESTED) {
            throw new IllegalStateException("Refund request is not dispatchable");
        }
        status = StripeRefundStatus.DISPATCHING;
        attemptCount += 1;
        nextAttemptAt = null;
        failureReason = null;
        updatedAt = now;
    }

    public void recordSubmission(String refundId, String providerStatus, LocalDateTime now) {
        attachRefundId(refundId);
        stripeStatus = normalize(providerStatus);
        if (submittedAt == null) {
            submittedAt = now;
        }
        if (providerEventCreatedAt == null) {
            applyProviderStatus(stripeStatus, now);
        }
        updatedAt = now;
    }

    public void reschedule(String failureReason, LocalDateTime nextAttemptAt, LocalDateTime now) {
        if (status != StripeRefundStatus.DISPATCHING) {
            return;
        }
        status = StripeRefundStatus.REQUESTED;
        this.failureReason = normalizeFailure(failureReason);
        this.nextAttemptAt = nextAttemptAt;
        updatedAt = now;
    }

    public void cancelBeforeFirstDispatch(String failureReason, LocalDateTime now) {
        if (status != StripeRefundStatus.REQUESTED || attemptCount != 0 || stripeRefundId != null) {
            throw new IllegalStateException("Refund can only be canceled locally before its first provider attempt");
        }
        status = StripeRefundStatus.CANCELED;
        this.failureReason = normalizeFailure(failureReason);
        nextAttemptAt = null;
        resolvedAt = now;
        updatedAt = now;
    }

    public void markReviewRequired(String failureReason, LocalDateTime now) {
        if (status != StripeRefundStatus.DISPATCHING && status != StripeRefundStatus.REQUESTED) {
            return;
        }
        status = StripeRefundStatus.REVIEW_REQUIRED;
        this.failureReason = normalizeFailure(failureReason);
        nextAttemptAt = null;
        resolvedAt = null;
        updatedAt = now;
    }

    public boolean applyProviderEvent(
            String refundId,
            String providerStatus,
            String providerFailureReason,
            Long eventCreatedAt,
            LocalDateTime now
    ) {
        attachRefundId(refundId);
        if (eventCreatedAt != null && providerEventCreatedAt != null && eventCreatedAt < providerEventCreatedAt) {
            return false;
        }
        if (eventCreatedAt != null) {
            providerEventCreatedAt = eventCreatedAt;
        }
        stripeStatus = normalize(providerStatus);
        failureReason = normalizeFailure(providerFailureReason);
        if (submittedAt == null) {
            submittedAt = now;
        }
        applyProviderStatus(stripeStatus, now);
        updatedAt = now;
        return true;
    }

    public boolean markWalletRestored(LocalDateTime now) {
        if (walletRestored) {
            return false;
        }
        walletRestored = true;
        updatedAt = now;
        return true;
    }

    private void attachRefundId(String refundId) {
        if (refundId == null || refundId.isBlank()) {
            throw new IllegalStateException("Stripe refund id is required");
        }
        if (stripeRefundId != null && !stripeRefundId.equals(refundId)) {
            throw new IllegalStateException("Refund request is already attached to another Stripe refund");
        }
        stripeRefundId = refundId;
    }

    private void applyProviderStatus(String providerStatus, LocalDateTime now) {
        StripeRefundStatus mapped = switch (providerStatus == null ? "" : providerStatus) {
            case "succeeded" -> StripeRefundStatus.SUCCEEDED;
            case "failed" -> StripeRefundStatus.FAILED;
            case "canceled" -> StripeRefundStatus.CANCELED;
            case "requires_action" -> StripeRefundStatus.REQUIRES_ACTION;
            default -> StripeRefundStatus.PENDING;
        };

        if ((status == StripeRefundStatus.FAILED || status == StripeRefundStatus.CANCELED)
                && mapped != StripeRefundStatus.FAILED
                && mapped != StripeRefundStatus.CANCELED) {
            return;
        }
        status = mapped;
        if (mapped == StripeRefundStatus.SUCCEEDED
                || mapped == StripeRefundStatus.FAILED
                || mapped == StripeRefundStatus.CANCELED) {
            resolvedAt = now;
        } else {
            resolvedAt = null;
        }
    }

    private String normalize(String value) {
        return value == null || value.isBlank() ? null : value.trim().toLowerCase();
    }

    private String normalizeFailure(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String normalized = value.trim().toLowerCase();
        return normalized.length() <= 64 ? normalized : normalized.substring(0, 64);
    }

    public Long getId() { return id; }
    public Long getUserId() { return userId; }
    public String getStripePaymentIntentId() { return stripePaymentIntentId; }
    public String getRequestKey() { return requestKey; }
    public BigDecimal getAmount() { return amount; }
    public String getCurrency() { return currency; }
    public StripeRefundStatus getStatus() { return status; }
    public String getStripeRefundId() { return stripeRefundId; }
    public String getStripeStatus() { return stripeStatus; }
    public String getFailureReason() { return failureReason; }
    public int getAttemptCount() { return attemptCount; }
    public LocalDateTime getNextAttemptAt() { return nextAttemptAt; }
    public Long getProviderEventCreatedAt() { return providerEventCreatedAt; }
    public boolean isWalletRestored() { return walletRestored; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public LocalDateTime getSubmittedAt() { return submittedAt; }
    public LocalDateTime getResolvedAt() { return resolvedAt; }
}
