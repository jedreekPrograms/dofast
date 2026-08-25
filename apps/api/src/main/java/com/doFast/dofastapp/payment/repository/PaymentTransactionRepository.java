package com.doFast.dofastapp.payment.repository;

import com.doFast.dofastapp.payment.entity.PaymentTransaction;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Optional;

public interface PaymentTransactionRepository extends JpaRepository<PaymentTransaction, Long> {

    Optional<PaymentTransaction> findByStripePaymentIntentId(String stripePaymentIntentId);

    Optional<PaymentTransaction> findByStripeEventId(String stripeEventId);

    @Modifying
    @Query(value = """
            INSERT INTO payment_transactions (
                stripe_payment_intent_id,
                stripe_event_id,
                user_id,
                amount,
                currency,
                processed_at
            ) VALUES (
                :paymentIntentId,
                :eventId,
                :userId,
                :amount,
                :currency,
                :processedAt
            )
            ON CONFLICT DO NOTHING
            """, nativeQuery = true)
    int claimSuccessfulPayment(
            @Param("paymentIntentId") String paymentIntentId,
            @Param("eventId") String eventId,
            @Param("userId") Long userId,
            @Param("amount") BigDecimal amount,
            @Param("currency") String currency,
            @Param("processedAt") LocalDateTime processedAt
    );

    @Query(value = """
            SELECT COUNT(*)
            FROM (
                SELECT p.id
                FROM payment_transactions p
                LEFT JOIN wallets w ON w.user_id = p.user_id
                LEFT JOIN wallet_transactions wt
                    ON wt.operation_key = 'stripe:intent:' || p.stripe_payment_intent_id
                WHERE p.stripe_event_id NOT LIKE 'legacy-event:%'
                  AND (
                      w.id IS NULL
                      OR wt.id IS NULL
                      OR wt.wallet_id <> w.id
                      OR wt.type <> 'TOP_UP'
                      OR wt.amount <> p.amount
                      OR wt.job_id IS NOT NULL
                      OR p.currency <> 'PLN'
                  )

                UNION ALL

                SELECT wt.id
                FROM wallet_transactions wt
                LEFT JOIN payment_transactions p
                    ON wt.operation_key = 'stripe:intent:' || p.stripe_payment_intent_id
                   AND p.stripe_event_id NOT LIKE 'legacy-event:%'
                WHERE wt.type = 'TOP_UP'
                  AND wt.operation_key LIKE 'stripe:intent:%'
                  AND p.id IS NULL
            ) mismatches
            """, nativeQuery = true)
    long countStripeLedgerMismatches();
}
