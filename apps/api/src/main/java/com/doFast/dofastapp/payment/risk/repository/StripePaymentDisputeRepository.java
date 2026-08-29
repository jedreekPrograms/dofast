package com.doFast.dofastapp.payment.risk.repository;

import com.doFast.dofastapp.payment.risk.entity.StripePaymentDispute;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

public interface StripePaymentDisputeRepository extends JpaRepository<StripePaymentDispute, Long> {

    Optional<StripePaymentDispute> findByStripeDisputeId(String stripeDisputeId);

    Optional<StripePaymentDispute> findByStripePaymentIntentId(String stripePaymentIntentId);

    boolean existsByUserIdAndOutstandingAmountGreaterThan(Long userId, BigDecimal amount);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select d from StripePaymentDispute d where d.id = :id")
    Optional<StripePaymentDispute> findByIdForUpdate(@Param("id") Long id);

    @Query(value = """
            SELECT id
            FROM stripe_payment_disputes
            WHERE outstanding_amount > 0
              AND funds_withdrawn = TRUE
              AND funds_reinstated = FALSE
            ORDER BY updated_at, id
            LIMIT :limit
            """, nativeQuery = true)
    List<Long> findRecoverableIds(@Param("limit") int limit);
}
