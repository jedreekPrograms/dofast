package com.doFast.dofastapp.job.publication;

import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface JobPublicationRepository extends JpaRepository<JobPublication, Long> {

    Optional<JobPublication> findByRequestKey(String requestKey);

    List<JobPublication> findAllByUser_IdAndStatusAndExpiresAtAfterOrderByCreatedAtDescIdDesc(
            Long userId,
            JobPublicationStatus status,
            LocalDateTime expiresAt
    );

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select publication from JobPublication publication join fetch publication.user where publication.id = :id")
    Optional<JobPublication> findByIdForUpdate(@Param("id") Long id);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<JobPublication> findFirstByStatusAndExpiresAtBeforeOrderByExpiresAtAscIdAsc(
            JobPublicationStatus status,
            LocalDateTime expiresAt
    );

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<JobPublication> findFirstByUser_IdAndStatusAndExpiresAtBeforeOrderByExpiresAtAscIdAsc(
            Long userId,
            JobPublicationStatus status,
            LocalDateTime expiresAt
    );

    @Query("""
            select publication.id
            from JobPublication publication
            where publication.status = :status
              and publication.stripePaymentIntentId is null
              and publication.stripePaymentIntentCreateStartedAt is not null
              and publication.paymentReceivedAt is null
              and publication.stripePaymentIntentCreateReviewRequired = false
              and publication.stripePaymentIntentCreateNextAttemptAt is not null
              and publication.stripePaymentIntentCreateNextAttemptAt <= :now
            order by publication.stripePaymentIntentCreateNextAttemptAt asc, publication.id asc
            """)
    List<Long> findDueStripePaymentIntentCreateRecoveryIds(
            @Param("status") JobPublicationStatus status,
            @Param("now") LocalDateTime now,
            Pageable pageable
    );

    @Query("""
            select publication.id
            from JobPublication publication
            where publication.status = :status
              and publication.stripePaymentIntentId is not null
              and publication.stripePaymentIntentCleanupCompletedAt is null
              and publication.stripePaymentIntentCleanupReviewRequired = false
              and publication.stripePaymentIntentCleanupNextAttemptAt is not null
              and publication.stripePaymentIntentCleanupNextAttemptAt <= :now
            order by publication.stripePaymentIntentCleanupNextAttemptAt asc, publication.id asc
            """)
    List<Long> findDueStripePaymentIntentCleanupIds(
            @Param("status") JobPublicationStatus status,
            @Param("now") LocalDateTime now,
            Pageable pageable
    );
}
