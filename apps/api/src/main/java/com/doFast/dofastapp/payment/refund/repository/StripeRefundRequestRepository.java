package com.doFast.dofastapp.payment.refund.repository;

import com.doFast.dofastapp.payment.refund.entity.StripeRefundRequest;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface StripeRefundRequestRepository extends JpaRepository<StripeRefundRequest, Long> {

    Optional<StripeRefundRequest> findByUserIdAndRequestKey(Long userId, String requestKey);

    Optional<StripeRefundRequest> findByStripeRefundId(String stripeRefundId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select r from StripeRefundRequest r where r.id = :id")
    Optional<StripeRefundRequest> findByIdForUpdate(@Param("id") Long id);

    @Query(value = """
            SELECT COALESCE(SUM(amount), 0)
            FROM stripe_refund_requests
            WHERE stripe_payment_intent_id = :paymentIntentId
              AND status NOT IN ('FAILED', 'CANCELED')
            """, nativeQuery = true)
    BigDecimal sumCommittedAmount(@Param("paymentIntentId") String paymentIntentId);

    @Query(value = """
            SELECT id
            FROM stripe_refund_requests
            WHERE status = 'REQUESTED'
              AND next_attempt_at <= :now
            ORDER BY next_attempt_at, id
            LIMIT :limit
            """, nativeQuery = true)
    List<Long> findDispatchableIds(@Param("now") LocalDateTime now, @Param("limit") int limit);

    @Modifying
    @Query(value = """
            UPDATE stripe_refund_requests
            SET status = 'REQUESTED',
                next_attempt_at = :now,
                updated_at = :now
            WHERE status = 'DISPATCHING'
              AND updated_at < :staleBefore
            """, nativeQuery = true)
    int requeueStaleDispatches(@Param("staleBefore") LocalDateTime staleBefore, @Param("now") LocalDateTime now);
}
