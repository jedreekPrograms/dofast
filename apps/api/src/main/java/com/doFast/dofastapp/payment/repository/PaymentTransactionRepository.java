package com.doFast.dofastapp.payment.repository;

import com.doFast.dofastapp.payment.entity.PaymentTransaction;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PaymentTransactionRepository extends JpaRepository<PaymentTransaction, Long> {
    boolean existsByStripePaymentIntentId(String stripePaymentIntentId);
}
