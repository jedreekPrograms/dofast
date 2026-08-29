package com.doFast.dofastapp.payment.refund.repository;

import com.doFast.dofastapp.payment.refund.entity.StripeRefundEvent;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;

public interface StripeRefundEventRepository extends JpaRepository<StripeRefundEvent, String> {

    @Modifying
    @Query(value = """
            INSERT INTO stripe_refund_events (
                stripe_event_id,
                refund_request_id,
                stripe_refund_id,
                event_type,
                provider_created_at,
                processed_at
            ) VALUES (
                :eventId,
                :requestId,
                :refundId,
                :eventType,
                :providerCreatedAt,
                :processedAt
            )
            ON CONFLICT DO NOTHING
            """, nativeQuery = true)
    int claim(
            @Param("eventId") String eventId,
            @Param("requestId") Long requestId,
            @Param("refundId") String refundId,
            @Param("eventType") String eventType,
            @Param("providerCreatedAt") Long providerCreatedAt,
            @Param("processedAt") LocalDateTime processedAt
    );
}
