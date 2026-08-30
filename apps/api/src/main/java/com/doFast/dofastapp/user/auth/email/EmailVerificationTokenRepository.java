package com.doFast.dofastapp.user.auth.email;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.Optional;

public interface EmailVerificationTokenRepository extends JpaRepository<EmailVerificationToken, Long> {

    Optional<EmailVerificationToken> findByTokenHash(String tokenHash);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select t from EmailVerificationToken t join fetch t.user where t.tokenHash = :tokenHash")
    Optional<EmailVerificationToken> findByTokenHashForUpdate(@Param("tokenHash") String tokenHash);

    @Modifying
    @Query("update EmailVerificationToken t set t.invalidatedAt = :now where t.user.id = :userId and t.usedAt is null and t.invalidatedAt is null")
    int invalidateActiveForUser(@Param("userId") Long userId, @Param("now") LocalDateTime now);

    @Modifying
    @Query("delete from EmailVerificationToken t where t.expiresAt < :cutoff or t.usedAt < :cutoff or t.invalidatedAt < :cutoff")
    int deleteExpiredOrConsumedBefore(@Param("cutoff") LocalDateTime cutoff);
}
