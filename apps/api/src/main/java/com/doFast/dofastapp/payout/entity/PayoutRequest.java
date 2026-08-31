package com.doFast.dofastapp.payout.entity;

import com.doFast.dofastapp.payout.enums.PayoutStatus;
import com.doFast.dofastapp.user.entity.User;
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
import jakarta.persistence.UniqueConstraint;
import jakarta.persistence.Version;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(
        name = "payout_requests",
        uniqueConstraints = @UniqueConstraint(name = "uk_payout_requests_request_key", columnNames = "request_key"),
        indexes = {
                @Index(name = "idx_payout_requests_user_requested", columnList = "user_id,requested_at"),
                @Index(name = "idx_payout_requests_dispatch", columnList = "provider_code,status,next_attempt_at,requested_at")
        }
)
public class PayoutRequest {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Version
    @Column(nullable = false)
    private int version;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(name = "request_key", nullable = false, length = 160)
    private String requestKey;

    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal amount;

    @Column(nullable = false, length = 3)
    private String currency;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private PayoutStatus status;

    @Column(name = "provider_code", nullable = false, length = 32)
    private String providerCode;

    @Column(name = "provider_reference", length = 255)
    private String providerReference;

    @Column(name = "provider_transfer_reference", length = 255)
    private String providerTransferReference;

    @Column(name = "provider_state_event_created_at")
    private Long providerStateEventCreatedAt;

    @Column(name = "attempt_count", nullable = false)
    private int attemptCount;

    @Column(name = "requested_at", nullable = false)
    private LocalDateTime requestedAt;

    @Column(name = "next_attempt_at", nullable = false)
    private LocalDateTime nextAttemptAt;

    @Column(name = "processing_started_at")
    private LocalDateTime processingStartedAt;

    @Column(name = "provider_submitted_at")
    private LocalDateTime providerSubmittedAt;

    @Column(name = "resolved_at")
    private LocalDateTime resolvedAt;

    @Column(name = "failure_code", length = 64)
    private String failureCode;

    @Column(name = "last_error_at")
    private LocalDateTime lastErrorAt;

    public PayoutRequest() {}

    public void initialize(
            User user,
            String requestKey,
            BigDecimal amount,
            String currency,
            String providerCode,
            LocalDateTime now
    ) {
        if (user == null || requestKey == null || requestKey.isBlank() || amount == null || amount.signum() <= 0) {
            throw new IllegalArgumentException("Invalid payout request initialization");
        }
        if (currency == null || currency.isBlank() || providerCode == null || providerCode.isBlank() || now == null) {
            throw new IllegalArgumentException("Payout currency, provider and time are required");
        }
        this.user = user;
        this.requestKey = requestKey;
        this.amount = amount;
        this.currency = currency;
        this.providerCode = providerCode;
        this.status = PayoutStatus.REQUESTED;
        this.attemptCount = 0;
        this.requestedAt = now;
        this.nextAttemptAt = now;
        this.processingStartedAt = null;
        this.providerSubmittedAt = null;
        this.resolvedAt = null;
        this.failureCode = null;
        this.lastErrorAt = null;
        this.providerReference = null;
        this.providerTransferReference = null;
        this.providerStateEventCreatedAt = null;
    }

    public void startProcessing(LocalDateTime now) {
        requireStatus(PayoutStatus.REQUESTED);
        status = PayoutStatus.PROCESSING;
        processingStartedAt = now;
        attemptCount++;
    }

    public void scheduleRetry(String code, LocalDateTime nextAttemptAt, LocalDateTime now) {
        requireStatus(PayoutStatus.PROCESSING);
        status = PayoutStatus.REQUESTED;
        processingStartedAt = null;
        this.nextAttemptAt = nextAttemptAt;
        failureCode = code;
        lastErrorAt = now;
    }

    public void requireReview(String code, LocalDateTime now) {
        if (status != PayoutStatus.REQUESTED && status != PayoutStatus.PROCESSING) {
            throw new IllegalStateException("Only queued or processing payouts can require review");
        }
        status = PayoutStatus.REVIEW_REQUIRED;
        processingStartedAt = null;
        failureCode = code;
        lastErrorAt = now;
    }

    public boolean recordProviderResponseForReview(
            String trustedTransferReference,
            String trustedPayoutReference,
            String code,
            LocalDateTime now
    ) {
        if (status != PayoutStatus.PROCESSING) {
            return false;
        }
        if (trustedTransferReference != null && !trustedTransferReference.isBlank()) {
            String normalized = trustedTransferReference.trim();
            if (normalized.length() <= 255
                    && (providerTransferReference == null || providerTransferReference.equals(normalized))) {
                providerTransferReference = normalized;
            }
        }
        if (trustedPayoutReference != null && !trustedPayoutReference.isBlank()) {
            String normalized = trustedPayoutReference.trim();
            if (normalized.length() <= 255
                    && (providerReference == null || providerReference.equals(normalized))) {
                providerReference = normalized;
            }
        }
        requireReview(code, now);
        return true;
    }

    public void retryByAdmin(LocalDateTime now) {
        requireStatus(PayoutStatus.REVIEW_REQUIRED);
        status = PayoutStatus.REQUESTED;
        processingStartedAt = null;
        nextAttemptAt = now;
        failureCode = null;
    }

    public void recordProviderTransferReference(String providerTransferReference) {
        requireStatus(PayoutStatus.PROCESSING);
        if (providerTransferReference == null || providerTransferReference.isBlank()) {
            throw new IllegalArgumentException("Provider transfer reference is required");
        }
        String normalized = providerTransferReference.trim();
        if (this.providerTransferReference != null && !this.providerTransferReference.equals(normalized)) {
            throw new IllegalStateException("Payout already has a different provider transfer reference");
        }
        this.providerTransferReference = normalized;
    }

    public void recordProviderStateEventCreatedAt(long eventCreatedAt) {
        if (eventCreatedAt <= 0) {
            throw new IllegalArgumentException("Provider state event timestamp must be positive");
        }
        if (providerStateEventCreatedAt != null && eventCreatedAt < providerStateEventCreatedAt) {
            throw new IllegalStateException("Provider state event timestamp cannot move backwards");
        }
        providerStateEventCreatedAt = eventCreatedAt;
    }

    public void markSubmitted(String providerReference, LocalDateTime now) {
        requireStatus(PayoutStatus.PROCESSING);
        requireProviderReference(providerReference);
        status = PayoutStatus.SUBMITTED;
        this.providerReference = providerReference.trim();
        processingStartedAt = null;
        providerSubmittedAt = now;
        failureCode = null;
    }

    public void recoverSubmittedProviderReference(String providerReference, LocalDateTime now) {
        if (status != PayoutStatus.PROCESSING && status != PayoutStatus.REVIEW_REQUIRED) {
            throw new IllegalStateException("Only ambiguous provider dispatch can recover a submitted payout reference");
        }
        requireProviderReference(providerReference);
        String normalized = providerReference.trim();
        if (this.providerReference != null && !this.providerReference.equals(normalized)) {
            throw new IllegalStateException("Payout already has a different provider reference");
        }
        status = PayoutStatus.SUBMITTED;
        this.providerReference = normalized;
        processingStartedAt = null;
        providerSubmittedAt = now;
        failureCode = null;
        lastErrorAt = null;
    }

    public void scheduleSubmittedReconciliation(LocalDateTime nextReconciliationAt) {
        requireStatus(PayoutStatus.SUBMITTED);
        if (nextReconciliationAt == null) {
            throw new IllegalArgumentException("Submitted payout reconciliation time is required");
        }
        this.nextAttemptAt = nextReconciliationAt;
    }

    public void recordSubmittedReconciliationFailure(String code, LocalDateTime now) {
        requireStatus(PayoutStatus.SUBMITTED);
        if (code == null || code.isBlank() || now == null) {
            throw new IllegalArgumentException("Submitted payout reconciliation failure is required");
        }
        failureCode = code.trim();
        lastErrorAt = now;
    }

    public void clearSubmittedReconciliationFailure() {
        requireStatus(PayoutStatus.SUBMITTED);
        failureCode = null;
    }

    public void markPaid(String providerReference, LocalDateTime now) {
        requireStatus(PayoutStatus.PROCESSING);
        requireProviderReference(providerReference);
        status = PayoutStatus.PAID;
        this.providerReference = providerReference.trim();
        processingStartedAt = null;
        resolvedAt = now;
        failureCode = null;
    }

    public void markSubmittedPaid(LocalDateTime now) {
        requireStatus(PayoutStatus.SUBMITTED);
        status = PayoutStatus.PAID;
        resolvedAt = now;
        failureCode = null;
    }

    public void markFailed(String code, LocalDateTime now) {
        if (status != PayoutStatus.PROCESSING
                && status != PayoutStatus.REVIEW_REQUIRED
                && status != PayoutStatus.SUBMITTED) {
            throw new IllegalStateException("Only processing, submitted or review-required payout can fail");
        }
        status = PayoutStatus.FAILED;
        processingStartedAt = null;
        resolvedAt = now;
        failureCode = code;
        lastErrorAt = now;
    }

    public void cancel(LocalDateTime now) {
        requireStatus(PayoutStatus.REQUESTED);
        status = PayoutStatus.CANCELLED;
        processingStartedAt = null;
        resolvedAt = now;
    }

    private void requireProviderReference(String providerReference) {
        if (providerReference == null || providerReference.isBlank()) {
            throw new IllegalArgumentException("Provider reference is required for submitted payout");
        }
    }

    private void requireStatus(PayoutStatus expected) {
        if (status != expected) {
            throw new IllegalStateException("Expected payout status " + expected + " but was " + status);
        }
    }

    public Long getId() { return id; }
    public User getUser() { return user; }
    public String getRequestKey() { return requestKey; }
    public BigDecimal getAmount() { return amount; }
    public String getCurrency() { return currency; }
    public PayoutStatus getStatus() { return status; }
    public String getProviderCode() { return providerCode; }
    public String getProviderReference() { return providerReference; }
    public String getProviderTransferReference() { return providerTransferReference; }
    public Long getProviderStateEventCreatedAt() { return providerStateEventCreatedAt; }
    public int getAttemptCount() { return attemptCount; }
    public LocalDateTime getRequestedAt() { return requestedAt; }
    public LocalDateTime getNextAttemptAt() { return nextAttemptAt; }
    public LocalDateTime getProcessingStartedAt() { return processingStartedAt; }
    public LocalDateTime getProviderSubmittedAt() { return providerSubmittedAt; }
    public LocalDateTime getResolvedAt() { return resolvedAt; }
    public String getFailureCode() { return failureCode; }
    public LocalDateTime getLastErrorAt() { return lastErrorAt; }
}
