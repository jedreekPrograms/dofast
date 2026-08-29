package com.doFast.dofastapp.payment.risk.repository;

import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;

public interface StripePaymentDisputeEventRepository extends Repository<com.doFast.dofastapp.payment.risk.entity.StripePaymentDispute, Long> {

    @Modifying
    @Query(value = """
            INSERT INTO stripe_payment_dispute_events (
                stripe_event_id,
                stripe_dispute_id,
                event_type,
                processed_at
            ) VALUES (
                :eventId,
                :disputeId,
                :eventType,
                :processedAt
            )
            ON CONFLICT (stripe_event_id) DO NOTHING
            """, nativeQuery = true)
    int claim(
            @Param("eventId") String eventId,
            @Param("disputeId") String disputeId,
            @Param("eventType") String eventType,
            @Param("processedAt") LocalDateTime processedAt
    );
}
