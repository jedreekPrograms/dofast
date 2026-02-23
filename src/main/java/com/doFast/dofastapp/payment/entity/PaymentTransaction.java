package com.doFast.dofastapp.payment.entity;


import jakarta.persistence.*;
import jakarta.persistence.UniqueConstraint;

import java.math.BigDecimal;

@Entity
@Table(uniqueConstraints = {
        @UniqueConstraint(columnNames = {"stripePaymentIntentId"})
})
public class PaymentTransaction {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String stripePaymentIntentId;

    private Long userId;

    private BigDecimal amount;

    public PaymentTransaction() {}

    public PaymentTransaction(String stripePaymentIntentId,
                              Long userId,
                              BigDecimal amount) {
        this.stripePaymentIntentId = stripePaymentIntentId;
        this.userId = userId;
        this.amount = amount;
    }

    public String getStripePaymentIntentId() {
        return stripePaymentIntentId;
    }
}
