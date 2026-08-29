package com.doFast.dofastapp.payment.repository;

import com.doFast.dofastapp.payment.entity.PaymentTransaction;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Optional;

public interface PaymentTransactionRepository extends JpaRepository<PaymentTransaction, Long> {

    Optional<PaymentTransaction> findByStripePaymentIntentId(String stripePaymentIntentId);

    Optional<PaymentTransaction> findByStripeEventId(String stripeEventId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from PaymentTransaction p where p.stripePaymentIntentId = :paymentIntentId")
    Optional<PaymentTransaction> findByStripePaymentIntentIdForUpdate(@Param("paymentIntentId") String paymentIntentId);

    @Modifying
    @Query(value = """
            INSERT INTO payment_transactions (
                stripe_payment_intent_id,
                stripe_event_id,
                user_id,
                amount,
                currency,
                settlement_purpose,
                business_reference,
                processed_at
            ) VALUES (
                :paymentIntentId,
                :eventId,
                :userId,
                :amount,
                :currency,
                :settlementPurpose,
                :businessReference,
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
            @Param("settlementPurpose") String settlementPurpose,
            @Param("businessReference") String businessReference,
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
                LEFT JOIN job_publications jp
                    ON jp.stripe_payment_intent_id = p.stripe_payment_intent_id
                WHERE p.stripe_event_id NOT LIKE 'legacy-event:%'
                  AND (
                      w.id IS NULL
                      OR wt.id IS NULL
                      OR wt.wallet_id <> w.id
                      OR p.settlement_purpose NOT IN ('TOP_UP', 'JOB_PUBLICATION')
                      OR wt.type <> CASE
                          WHEN p.settlement_purpose = 'JOB_PUBLICATION' THEN 'JOB_PUBLICATION_FUNDING'
                          ELSE 'TOP_UP'
                      END
                      OR wt.amount <> p.amount
                      OR wt.job_id IS NOT NULL
                      OR p.currency <> 'PLN'
                      OR (
                          p.settlement_purpose = 'JOB_PUBLICATION'
                          AND (
                              p.business_reference IS NULL
                              OR jp.id IS NULL
                              OR p.business_reference <> jp.id::text
                          )
                      )
                      OR (
                          p.settlement_purpose = 'TOP_UP'
                          AND jp.id IS NOT NULL
                      )
                  )

                UNION ALL

                SELECT wt.id
                FROM wallet_transactions wt
                LEFT JOIN payment_transactions p
                    ON wt.operation_key = 'stripe:intent:' || p.stripe_payment_intent_id
                   AND p.stripe_event_id NOT LIKE 'legacy-event:%'
                WHERE wt.type IN ('TOP_UP', 'JOB_PUBLICATION_FUNDING')
                  AND wt.operation_key LIKE 'stripe:intent:%'
                  AND p.id IS NULL
            ) mismatches
            """, nativeQuery = true)
    long countStripeLedgerMismatches();
}
