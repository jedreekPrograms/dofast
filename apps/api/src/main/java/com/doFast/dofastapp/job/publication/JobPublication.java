package com.doFast.dofastapp.job.publication;

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
import jakarta.persistence.Version;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Objects;
import java.util.UUID;

@Entity
@Table(name = "job_publications")
public class JobPublication {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Version
    @Column(nullable = false)
    private int version;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(name = "request_key", nullable = false, unique = true, length = 160)
    private String requestKey;

    @Column(name = "payload_hash", nullable = false, length = 64)
    private String payloadHash;

    @Column(name = "request_payload", columnDefinition = "text")
    private String requestPayload;

    @Column(name = "category_id", nullable = false)
    private Long categoryId;

    @Column(name = "route_quote_id")
    private UUID routeQuoteId;

    @Column(name = "total_amount", nullable = false, precision = 19, scale = 2)
    private BigDecimal totalAmount;

    @Column(name = "wallet_reserved_amount", nullable = false, precision = 19, scale = 2)
    private BigDecimal walletReservedAmount;

    @Column(name = "payment_amount", nullable = false, precision = 19, scale = 2)
    private BigDecimal paymentAmount;

    @Column(nullable = false, length = 3)
    private String currency;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private JobPublicationStatus status;

    @Column(name = "stripe_payment_intent_id", unique = true, length = 255)
    private String stripePaymentIntentId;

    @Column(name = "published_job_id", unique = true)
    private Long publishedJobId;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @Column(name = "expires_at", nullable = false)
    private LocalDateTime expiresAt;

    @Column(name = "payment_received_at")
    private LocalDateTime paymentReceivedAt;

    @Enumerated(EnumType.STRING)
    @Column(name = "recovery_reason", length = 48)
    private JobPublicationRecoveryReason recoveryReason;

    @Column(name = "published_at")
    private LocalDateTime publishedAt;

    @Column(name = "cancelled_at")
    private LocalDateTime cancelledAt;

    @Column(name = "stripe_create_started_at")
    private LocalDateTime stripePaymentIntentCreateStartedAt;

    @Column(name = "stripe_create_attempt_count", nullable = false)
    private int stripePaymentIntentCreateAttemptCount;

    @Column(name = "stripe_create_next_attempt_at")
    private LocalDateTime stripePaymentIntentCreateNextAttemptAt;

    @Column(name = "stripe_create_review_required", nullable = false)
    private boolean stripePaymentIntentCreateReviewRequired;

    @Column(name = "stripe_create_last_error", length = 128)
    private String stripePaymentIntentCreateLastError;

    @Column(name = "stripe_cleanup_attempt_count", nullable = false)
    private int stripePaymentIntentCleanupAttemptCount;

    @Column(name = "stripe_cleanup_next_attempt_at")
    private LocalDateTime stripePaymentIntentCleanupNextAttemptAt;

    @Column(name = "stripe_cleanup_completed_at")
    private LocalDateTime stripePaymentIntentCleanupCompletedAt;

    @Column(name = "stripe_cleanup_review_required", nullable = false)
    private boolean stripePaymentIntentCleanupReviewRequired;

    @Column(name = "stripe_cleanup_last_error", length = 128)
    private String stripePaymentIntentCleanupLastError;

    public JobPublication() {}

    public void initializePaymentRequired(
            User user,
            String requestKey,
            String payloadHash,
            String requestPayload,
            Long categoryId,
            UUID routeQuoteId,
            BigDecimal totalAmount,
            BigDecimal walletReservedAmount,
            BigDecimal paymentAmount,
            LocalDateTime now,
            LocalDateTime expiresAt
    ) {
        this.user = user;
        this.requestKey = requestKey;
        this.payloadHash = payloadHash;
        this.requestPayload = requestPayload;
        this.categoryId = categoryId;
        this.routeQuoteId = routeQuoteId;
        this.totalAmount = totalAmount;
        this.walletReservedAmount = walletReservedAmount;
        this.paymentAmount = paymentAmount;
        this.currency = "PLN";
        this.status = JobPublicationStatus.PAYMENT_REQUIRED;
        this.createdAt = now;
        this.updatedAt = now;
        this.expiresAt = expiresAt;
    }

    public void initializePublished(
            User user,
            String requestKey,
            String payloadHash,
            Long categoryId,
            UUID routeQuoteId,
            BigDecimal totalAmount,
            Long publishedJobId,
            LocalDateTime now
    ) {
        this.user = user;
        this.requestKey = requestKey;
        this.payloadHash = payloadHash;
        this.requestPayload = null;
        this.categoryId = categoryId;
        this.routeQuoteId = routeQuoteId;
        this.totalAmount = totalAmount;
        this.walletReservedAmount = BigDecimal.ZERO.setScale(2);
        this.paymentAmount = BigDecimal.ZERO.setScale(2);
        this.currency = "PLN";
        this.status = JobPublicationStatus.PUBLISHED;
        this.publishedJobId = publishedJobId;
        this.createdAt = now;
        this.updatedAt = now;
        this.expiresAt = now;
        this.publishedAt = now;
    }

    public boolean claimStripePaymentIntentCreate(LocalDateTime now, LocalDateTime leaseUntil) {
        if (stripePaymentIntentId != null
                || paymentReceivedAt != null
                || stripePaymentIntentCreateReviewRequired
                || (stripePaymentIntentCreateNextAttemptAt != null
                && stripePaymentIntentCreateNextAttemptAt.isAfter(now))) {
            return false;
        }
        if (stripePaymentIntentCreateStartedAt == null) {
            stripePaymentIntentCreateStartedAt = now;
        }
        stripePaymentIntentCreateAttemptCount++;
        stripePaymentIntentCreateNextAttemptAt = Objects.requireNonNull(leaseUntil, "leaseUntil");
        stripePaymentIntentCreateLastError = null;
        updatedAt = now;
        return true;
    }

    public void retryStripePaymentIntentCreate(String failureCode, LocalDateTime nextAttemptAt, LocalDateTime now) {
        if (stripePaymentIntentId != null || paymentReceivedAt != null) {
            completeStripePaymentIntentCreate(now);
            return;
        }
        stripePaymentIntentCreateNextAttemptAt = Objects.requireNonNull(nextAttemptAt, "nextAttemptAt");
        stripePaymentIntentCreateLastError = normalizeProviderError(failureCode);
        updatedAt = now;
    }

    public void requireStripePaymentIntentCreateReview(String failureCode, LocalDateTime now) {
        stripePaymentIntentCreateReviewRequired = true;
        stripePaymentIntentCreateNextAttemptAt = null;
        stripePaymentIntentCreateLastError = normalizeProviderError(failureCode);
        updatedAt = now;
    }

    private void completeStripePaymentIntentCreate(LocalDateTime now) {
        stripePaymentIntentCreateNextAttemptAt = null;
        stripePaymentIntentCreateReviewRequired = false;
        stripePaymentIntentCreateLastError = null;
        updatedAt = now;
    }

    public void attachStripePaymentIntent(String paymentIntentId, LocalDateTime now) {
        if (paymentIntentId == null || paymentIntentId.isBlank()) {
            throw new IllegalArgumentException("Stripe PaymentIntent id is required");
        }
        if (this.stripePaymentIntentId != null && !this.stripePaymentIntentId.equals(paymentIntentId)) {
            throw new IllegalStateException("Publikacja ma już inny Stripe PaymentIntent");
        }
        this.stripePaymentIntentId = paymentIntentId;
        completeStripePaymentIntentCreate(now);
        if (status == JobPublicationStatus.CANCELLED && paymentReceivedAt == null) {
            scheduleStripePaymentIntentCleanup(now);
        }
    }

    public void recordSuccessfulPayment(LocalDateTime now) {
        if (this.paymentReceivedAt == null) {
            this.paymentReceivedAt = now;
        }
        completeStripePaymentIntentCreate(now);
        this.updatedAt = now;
    }

    public void markPublished(Long jobId, LocalDateTime now) {
        this.status = JobPublicationStatus.PUBLISHED;
        this.publishedJobId = jobId;
        this.requestPayload = null;
        this.recoveryReason = null;
        this.publishedAt = now;
        this.updatedAt = now;
    }

    public void markPaymentReceived(JobPublicationRecoveryReason recoveryReason, LocalDateTime now) {
        this.status = JobPublicationStatus.PAYMENT_RECEIVED;
        this.requestPayload = null;
        this.recoveryReason = Objects.requireNonNull(recoveryReason, "recoveryReason");
        recordSuccessfulPayment(now);
    }

    public void markLatePaymentAfterCancellation(LocalDateTime now) {
        if (this.status != JobPublicationStatus.CANCELLED) {
            throw new IllegalStateException("Tylko anulowana publikacja może otrzymać późne potwierdzenie płatności");
        }
        this.recoveryReason = JobPublicationRecoveryReason.CANCELLED_BEFORE_PAYMENT_CONFIRMED;
        recordSuccessfulPayment(now);
        completeStripePaymentIntentCleanup(now);
    }

    public void cancel(LocalDateTime now) {
        this.status = JobPublicationStatus.CANCELLED;
        this.requestPayload = null;
        this.recoveryReason = null;
        this.cancelledAt = now;
        this.updatedAt = now;
        scheduleStripePaymentIntentCleanup(now);
        scheduleStripePaymentIntentCreateRecovery(now);
    }

    private void scheduleStripePaymentIntentCreateRecovery(LocalDateTime now) {
        if (stripePaymentIntentId != null
                || paymentReceivedAt != null
                || stripePaymentIntentCreateStartedAt == null
                || stripePaymentIntentCreateReviewRequired) {
            return;
        }
        if (stripePaymentIntentCreateNextAttemptAt == null) {
            stripePaymentIntentCreateNextAttemptAt = now;
        }
    }

    private void scheduleStripePaymentIntentCleanup(LocalDateTime now) {
        if (stripePaymentIntentId == null || stripePaymentIntentId.isBlank() || paymentReceivedAt != null) {
            return;
        }
        stripePaymentIntentCleanupAttemptCount = 0;
        stripePaymentIntentCleanupNextAttemptAt = now;
        stripePaymentIntentCleanupCompletedAt = null;
        stripePaymentIntentCleanupReviewRequired = false;
        stripePaymentIntentCleanupLastError = null;
    }

    public boolean claimStripePaymentIntentCleanup(LocalDateTime now, LocalDateTime leaseUntil) {
        if (status != JobPublicationStatus.CANCELLED
                || stripePaymentIntentId == null
                || stripePaymentIntentId.isBlank()
                || paymentReceivedAt != null
                || stripePaymentIntentCleanupCompletedAt != null
                || stripePaymentIntentCleanupReviewRequired
                || stripePaymentIntentCleanupNextAttemptAt == null
                || stripePaymentIntentCleanupNextAttemptAt.isAfter(now)) {
            return false;
        }
        stripePaymentIntentCleanupAttemptCount++;
        stripePaymentIntentCleanupNextAttemptAt = leaseUntil;
        stripePaymentIntentCleanupLastError = null;
        updatedAt = now;
        return true;
    }

    public void completeStripePaymentIntentCleanup(LocalDateTime now) {
        stripePaymentIntentCleanupCompletedAt = now;
        stripePaymentIntentCleanupNextAttemptAt = null;
        stripePaymentIntentCleanupReviewRequired = false;
        stripePaymentIntentCleanupLastError = null;
        updatedAt = now;
    }

    public void retryStripePaymentIntentCleanup(String failureCode, LocalDateTime nextAttemptAt, LocalDateTime now) {
        stripePaymentIntentCleanupNextAttemptAt = Objects.requireNonNull(nextAttemptAt, "nextAttemptAt");
        stripePaymentIntentCleanupLastError = normalizeProviderError(failureCode);
        updatedAt = now;
    }

    public void requireStripePaymentIntentCleanupReview(String failureCode, LocalDateTime now) {
        stripePaymentIntentCleanupReviewRequired = true;
        stripePaymentIntentCleanupNextAttemptAt = null;
        stripePaymentIntentCleanupLastError = normalizeProviderError(failureCode);
        updatedAt = now;
    }

    private String normalizeProviderError(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String normalized = value.trim();
        return normalized.length() <= 128 ? normalized : normalized.substring(0, 128);
    }

    public Long getId() { return id; }
    public User getUser() { return user; }
    public String getRequestKey() { return requestKey; }
    public String getPayloadHash() { return payloadHash; }
    public String getRequestPayload() { return requestPayload; }
    public Long getCategoryId() { return categoryId; }
    public UUID getRouteQuoteId() { return routeQuoteId; }
    public BigDecimal getTotalAmount() { return totalAmount; }
    public BigDecimal getWalletReservedAmount() { return walletReservedAmount; }
    public BigDecimal getPaymentAmount() { return paymentAmount; }
    public String getCurrency() { return currency; }
    public JobPublicationStatus getStatus() { return status; }
    public String getStripePaymentIntentId() { return stripePaymentIntentId; }
    public Long getPublishedJobId() { return publishedJobId; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public LocalDateTime getExpiresAt() { return expiresAt; }
    public LocalDateTime getPaymentReceivedAt() { return paymentReceivedAt; }
    public JobPublicationRecoveryReason getRecoveryReason() { return recoveryReason; }
    public LocalDateTime getPublishedAt() { return publishedAt; }
    public LocalDateTime getCancelledAt() { return cancelledAt; }
    public LocalDateTime getStripePaymentIntentCreateStartedAt() { return stripePaymentIntentCreateStartedAt; }
    public int getStripePaymentIntentCreateAttemptCount() { return stripePaymentIntentCreateAttemptCount; }
    public LocalDateTime getStripePaymentIntentCreateNextAttemptAt() { return stripePaymentIntentCreateNextAttemptAt; }
    public boolean isStripePaymentIntentCreateReviewRequired() { return stripePaymentIntentCreateReviewRequired; }
    public String getStripePaymentIntentCreateLastError() { return stripePaymentIntentCreateLastError; }
    public int getStripePaymentIntentCleanupAttemptCount() { return stripePaymentIntentCleanupAttemptCount; }
    public LocalDateTime getStripePaymentIntentCleanupNextAttemptAt() { return stripePaymentIntentCleanupNextAttemptAt; }
    public LocalDateTime getStripePaymentIntentCleanupCompletedAt() { return stripePaymentIntentCleanupCompletedAt; }
    public boolean isStripePaymentIntentCleanupReviewRequired() { return stripePaymentIntentCleanupReviewRequired; }
    public String getStripePaymentIntentCleanupLastError() { return stripePaymentIntentCleanupLastError; }
}
