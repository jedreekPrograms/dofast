package com.doFast.dofastapp.verification.repository;

import com.doFast.dofastapp.verification.entity.VerificationCase;
import com.doFast.dofastapp.verification.enums.VerificationStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface VerificationCaseRepository extends JpaRepository<VerificationCase, Long> {

    @EntityGraph(attributePaths = {"user", "reviewedBy"})
    Optional<VerificationCase> findByUser_Id(Long userId);

    boolean existsByUser_IdAndStatus(Long userId, VerificationStatus status);

    long countByStatus(VerificationStatus status);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @EntityGraph(attributePaths = {"user", "reviewedBy"})
    @Query("select verification from VerificationCase verification where verification.id = :id")
    Optional<VerificationCase> findByIdForUpdate(@Param("id") Long id);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @EntityGraph(attributePaths = {"user", "reviewedBy"})
    @Query("select verification from VerificationCase verification where verification.user.id = :userId")
    Optional<VerificationCase> findByUserIdForUpdate(@Param("userId") Long userId);

    @EntityGraph(attributePaths = {"user", "reviewedBy"})
    Page<VerificationCase> findByStatus(VerificationStatus status, Pageable pageable);

    @Override
    @EntityGraph(attributePaths = {"user", "reviewedBy"})
    Page<VerificationCase> findAll(Pageable pageable);
}
