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

@Entity
@Table(
        name = "payment_transactions",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_payment_transactions_stripe_intent",
                columnNames = "stripe_payment_intent_id"
        ),
        indexes = @Index(name = "idx_payment_transactions_user", columnList = "user_id")
)
public class PaymentTransaction {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "stripe_payment_intent_id", nullable = false, length = 255)
    private String stripePaymentIntentId;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal amount;

    public PaymentTransaction() {}

    public PaymentTransaction(String stripePaymentIntentId, Long userId, BigDecimal amount) {
        this.stripePaymentIntentId = stripePaymentIntentId;
        this.userId = userId;
        this.amount = amount;
    }

    public String getStripePaymentIntentId() { return stripePaymentIntentId; }
}
