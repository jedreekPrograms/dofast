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

    @Column(name = "published_at")
    private LocalDateTime publishedAt;

    @Column(name = "cancelled_at")
    private LocalDateTime cancelledAt;

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

    public void attachStripePaymentIntent(String paymentIntentId, LocalDateTime now) {
        this.stripePaymentIntentId = paymentIntentId;
        this.updatedAt = now;
    }

    public void markPublished(Long jobId, LocalDateTime now) {
        this.status = JobPublicationStatus.PUBLISHED;
        this.publishedJobId = jobId;
        this.requestPayload = null;
        this.publishedAt = now;
        this.updatedAt = now;
    }

    public void markPaymentReceived(LocalDateTime now) {
        this.status = JobPublicationStatus.PAYMENT_RECEIVED;
        this.requestPayload = null;
        this.updatedAt = now;
    }

    public void cancel(LocalDateTime now) {
        this.status = JobPublicationStatus.CANCELLED;
        this.requestPayload = null;
        this.cancelledAt = now;
        this.updatedAt = now;
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
    public LocalDateTime getPublishedAt() { return publishedAt; }
    public LocalDateTime getCancelledAt() { return cancelledAt; }
}
