package com.doFast.dofastapp.payment.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(
        name = "payment_transactions",
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "uk_payment_transactions_stripe_intent",
                        columnNames = "stripe_payment_intent_id"
                ),
                @UniqueConstraint(
                        name = "uk_payment_transactions_stripe_event",
                        columnNames = "stripe_event_id"
                )
        },
        indexes = {
                @Index(name = "idx_payment_transactions_user", columnList = "user_id"),
                @Index(name = "idx_payment_transactions_processed", columnList = "processed_at"),
                @Index(
                        name = "idx_payment_transactions_settlement_identity",
                        columnList = "settlement_purpose,business_reference"
                )
        }
)
public class PaymentTransaction {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "stripe_payment_intent_id", nullable = false, length = 255)
    private String stripePaymentIntentId;

    @Column(name = "stripe_event_id", nullable = false, length = 255)
    private String stripeEventId;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal amount;

    @Column(nullable = false, length = 3)
    private String currency;

    @Column(name = "settlement_purpose", nullable = false, length = 32)
    private String settlementPurpose;

    @Column(name = "business_reference", length = 128)
    private String businessReference;

    @Column(name = "processed_at", nullable = false)
    private LocalDateTime processedAt;

    public PaymentTransaction() {}

    public String getStripePaymentIntentId() { return stripePaymentIntentId; }
    public String getStripeEventId() { return stripeEventId; }
    public Long getUserId() { return userId; }
    public BigDecimal getAmount() { return amount; }
    public String getCurrency() { return currency; }
    public String getSettlementPurpose() { return settlementPurpose; }
    public String getBusinessReference() { return businessReference; }
    public LocalDateTime getProcessedAt() { return processedAt; }
}
