package com.doFast.dofastapp.payment.risk.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "stripe_payment_disputes")
public class StripePaymentDispute {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Version
    @Column(nullable = false)
    private int version;

    @Column(name = "stripe_dispute_id", nullable = false, length = 255)
    private String stripeDisputeId;

    @Column(name = "stripe_payment_intent_id", nullable = false, length = 255)
    private String stripePaymentIntentId;

    @Column(name = "stripe_charge_id", length = 255)
    private String stripeChargeId;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "disputed_amount", nullable = false, precision = 19, scale = 2)
    private BigDecimal disputedAmount;

    @Column(nullable = false, length = 3)
    private String currency;

    @Column(length = 64)
    private String reason;

    @Column(name = "stripe_status", nullable = false, length = 32)
    private String stripeStatus;

    @Column(name = "stripe_state_event_created_at")
    private LocalDateTime stripeStateEventCreatedAt;

    @Column(name = "funds_withdrawn", nullable = false)
    private boolean fundsWithdrawn;

    @Column(name = "funds_reinstated", nullable = false)
    private boolean fundsReinstated;

    @Column(name = "wallet_recovered_amount", nullable = false, precision = 19, scale = 2)
    private BigDecimal walletRecoveredAmount;

    @Column(name = "wallet_returned_amount", nullable = false, precision = 19, scale = 2)
    private BigDecimal walletReturnedAmount;

    @Column(name = "outstanding_amount", nullable = false, precision = 19, scale = 2)
    private BigDecimal outstandingAmount;

    @Column(name = "recovery_sequence", nullable = false)
    private int recoverySequence;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @Column(name = "funds_withdrawn_at")
    private LocalDateTime fundsWithdrawnAt;

    @Column(name = "funds_reinstated_at")
    private LocalDateTime fundsReinstatedAt;

    public StripePaymentDispute() {}

    public Long getId() { return id; }
    public String getStripeDisputeId() { return stripeDisputeId; }
    public String getStripePaymentIntentId() { return stripePaymentIntentId; }
    public String getStripeChargeId() { return stripeChargeId; }
    public Long getUserId() { return userId; }
    public BigDecimal getDisputedAmount() { return disputedAmount; }
    public String getCurrency() { return currency; }
    public String getReason() { return reason; }
    public String getStripeStatus() { return stripeStatus; }
    public LocalDateTime getStripeStateEventCreatedAt() { return stripeStateEventCreatedAt; }
    public boolean isFundsWithdrawn() { return fundsWithdrawn; }
    public boolean isFundsReinstated() { return fundsReinstated; }
    public BigDecimal getWalletRecoveredAmount() { return walletRecoveredAmount; }
    public BigDecimal getWalletReturnedAmount() { return walletReturnedAmount; }
    public BigDecimal getOutstandingAmount() { return outstandingAmount; }
    public int getRecoverySequence() { return recoverySequence; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }

    public void initialize(
            String disputeId,
            String paymentIntentId,
            String chargeId,
            Long userId,
            BigDecimal amount,
            String currency,
            String reason,
            String status,
            LocalDateTime now
    ) {
        initialize(disputeId, paymentIntentId, chargeId, userId, amount, currency, reason, status, now, null);
    }

    public void initialize(
            String disputeId,
            String paymentIntentId,
            String chargeId,
            Long userId,
            BigDecimal amount,
            String currency,
            String reason,
            String status,
            LocalDateTime now,
            LocalDateTime stripeEventCreatedAt
    ) {
        this.stripeDisputeId = disputeId;
        this.stripePaymentIntentId = paymentIntentId;
        this.stripeChargeId = chargeId;
        this.userId = userId;
        this.disputedAmount = amount;
        this.currency = currency;
        this.reason = reason;
        this.stripeStatus = status;
        this.stripeStateEventCreatedAt = stripeEventCreatedAt;
        this.fundsWithdrawn = false;
        this.fundsReinstated = false;
        this.walletRecoveredAmount = BigDecimal.ZERO.setScale(2);
        this.walletReturnedAmount = BigDecimal.ZERO.setScale(2);
        this.outstandingAmount = BigDecimal.ZERO.setScale(2);
        this.recoverySequence = 0;
        this.createdAt = now;
        this.updatedAt = now;
    }

    public void refresh(String chargeId, String reason, String status, LocalDateTime now) {
        refreshIdentity(chargeId);
        this.reason = reason;
        this.stripeStatus = status;
        this.updatedAt = now;
    }

    public boolean refreshFromStripeEvent(
            String chargeId,
            String reason,
            String status,
            LocalDateTime stripeEventCreatedAt,
            LocalDateTime now
    ) {
        if (stripeEventCreatedAt == null) {
            throw new IllegalArgumentException("Stripe event creation time is required");
        }
        if (this.stripeStateEventCreatedAt != null
                && stripeEventCreatedAt.isBefore(this.stripeStateEventCreatedAt)) {
            return false;
        }
        refreshIdentity(chargeId);
        this.reason = reason;
        this.stripeStatus = status;
        this.stripeStateEventCreatedAt = stripeEventCreatedAt;
        this.updatedAt = now;
        return true;
    }

    private void refreshIdentity(String chargeId) {
        if (chargeId != null && !chargeId.isBlank()) {
            if (stripeChargeId != null && !stripeChargeId.equals(chargeId)) {
                throw new IllegalStateException("Stripe dispute changed charge identity");
            }
            stripeChargeId = chargeId;
        }
    }

    public void markFundsWithdrawn(LocalDateTime now) {
        if (!fundsWithdrawn) {
            fundsWithdrawn = true;
            fundsWithdrawnAt = now;
        }
        if (!fundsReinstated) {
            outstandingAmount = disputedAmount.subtract(walletRecoveredAmount);
        }
        updatedAt = now;
    }

    public void recordWalletRecovery(BigDecimal amount, LocalDateTime now) {
        if (!fundsWithdrawn || fundsReinstated) {
            throw new IllegalStateException("Stripe dispute is not recoverable from wallet");
        }
        BigDecimal next = walletRecoveredAmount.add(amount).setScale(2);
        if (amount.signum() <= 0 || next.compareTo(disputedAmount) > 0) {
            throw new IllegalArgumentException("Invalid chargeback recovery amount");
        }
        walletRecoveredAmount = next;
        outstandingAmount = disputedAmount.subtract(walletRecoveredAmount).setScale(2);
        recoverySequence++;
        updatedAt = now;
    }

    public BigDecimal amountToReturnToWallet() {
        return walletRecoveredAmount.subtract(walletReturnedAmount).setScale(2);
    }

    public void markFundsReinstated(BigDecimal returnedAmount, LocalDateTime now) {
        if (returnedAmount.signum() < 0 || returnedAmount.compareTo(amountToReturnToWallet()) > 0) {
            throw new IllegalArgumentException("Invalid chargeback reinstatement amount");
        }
        walletReturnedAmount = walletReturnedAmount.add(returnedAmount).setScale(2);
        fundsReinstated = true;
        fundsReinstatedAt = now;
        outstandingAmount = BigDecimal.ZERO.setScale(2);
        updatedAt = now;
    }
}
