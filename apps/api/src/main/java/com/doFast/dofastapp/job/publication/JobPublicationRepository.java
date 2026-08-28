package com.doFast.dofastapp.job.publication;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.Optional;

public interface JobPublicationRepository extends JpaRepository<JobPublication, Long> {

    Optional<JobPublication> findByRequestKey(String requestKey);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select publication from JobPublication publication join fetch publication.user where publication.id = :id")
    Optional<JobPublication> findByIdForUpdate(@Param("id") Long id);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<JobPublication> findFirstByStatusAndExpiresAtBeforeOrderByExpiresAtAscIdAsc(
            JobPublicationStatus status,
            LocalDateTime expiresAt
    );
}
