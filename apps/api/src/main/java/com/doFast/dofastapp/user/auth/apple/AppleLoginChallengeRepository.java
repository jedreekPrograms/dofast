package com.doFast.dofastapp.user.auth.apple;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

public interface AppleLoginChallengeRepository extends JpaRepository<AppleLoginChallenge, UUID> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select challenge from AppleLoginChallenge challenge where challenge.id = :id")
    Optional<AppleLoginChallenge> findByIdForUpdate(@Param("id") UUID id);

    long deleteByExpiresAtBefore(Instant threshold);
}