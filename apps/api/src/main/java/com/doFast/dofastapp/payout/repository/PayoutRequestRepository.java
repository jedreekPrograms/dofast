package com.doFast.dofastapp.payout.repository;

import com.doFast.dofastapp.payout.entity.PayoutRequest;
import com.doFast.dofastapp.payout.enums.PayoutStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface PayoutRequestRepository extends JpaRepository<PayoutRequest, Long> {

    @EntityGraph(attributePaths = "user")
    Optional<PayoutRequest> findByRequestKey(String requestKey);

    @EntityGraph(attributePaths = "user")
    List<PayoutRequest> findByUser_IdOrderByRequestedAtDescIdDesc(Long userId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @EntityGraph(attributePaths = "user")
    @Query("select payout from PayoutRequest payout where payout.id = :id")
    Optional<PayoutRequest> findByIdForUpdate(@Param("id") Long id);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @EntityGraph(attributePaths = "user")
    @Query("""
            select payout from PayoutRequest payout
            where payout.providerCode = :providerCode
              and payout.providerReference = :providerReference
            """)
    Optional<PayoutRequest> findByProviderReferenceForUpdate(
            @Param("providerCode") String providerCode,
            @Param("providerReference") String providerReference
    );

    @Query(value = """
            SELECT *
            FROM payout_requests
            WHERE status = 'REQUESTED'
              AND provider_code = :providerCode
              AND next_attempt_at <= :now
            ORDER BY requested_at, id
            FOR UPDATE SKIP LOCKED
            LIMIT 1
            """, nativeQuery = true)
    Optional<PayoutRequest> findNextDispatchableForUpdate(
            @Param("providerCode") String providerCode,
            @Param("now") LocalDateTime now
    );

    @Query(value = """
            SELECT *
            FROM payout_requests
            WHERE status = 'SUBMITTED'
              AND provider_code = :providerCode
              AND provider_reference IS NOT NULL
              AND provider_transfer_reference IS NOT NULL
              AND next_attempt_at <= :now
            ORDER BY next_attempt_at, provider_submitted_at, id
            FOR UPDATE SKIP LOCKED
            LIMIT 1
            """, nativeQuery = true)
    Optional<PayoutRequest> findNextSubmittedForReconciliationForUpdate(
            @Param("providerCode") String providerCode,
            @Param("now") LocalDateTime now
    );

    @Query(value = """
            SELECT *
            FROM payout_requests
            WHERE status = 'PROCESSING'
              AND processing_started_at < :cutoff
            ORDER BY processing_started_at, id
            FOR UPDATE SKIP LOCKED
            LIMIT 1
            """, nativeQuery = true)
    Optional<PayoutRequest> findStaleProcessingForUpdate(@Param("cutoff") LocalDateTime cutoff);

    @EntityGraph(attributePaths = "user")
    Page<PayoutRequest> findByStatus(PayoutStatus status, Pageable pageable);

    @Override
    @EntityGraph(attributePaths = "user")
    Page<PayoutRequest> findAll(Pageable pageable);
}
