package com.doFast.dofastapp.user.auth.password;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.Optional;

public interface PasswordResetTokenRepository extends JpaRepository<PasswordResetToken, Long> {

    @EntityGraph(attributePaths = "user")
    Optional<PasswordResetToken> findByTokenHash(String tokenHash);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @EntityGraph(attributePaths = "user")
    @Query("select token from PasswordResetToken token where token.tokenHash = :tokenHash")
    Optional<PasswordResetToken> findByTokenHashForUpdate(@Param("tokenHash") String tokenHash);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            update PasswordResetToken token
            set token.invalidatedAt = :now
            where token.user.id = :userId
              and token.usedAt is null
              and token.invalidatedAt is null
            """)
    int invalidateActiveForUser(
            @Param("userId") Long userId,
            @Param("now") LocalDateTime now
    );

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            update PasswordResetToken token
            set token.invalidatedAt = :now
            where token.user.id = :userId
              and token.id <> :currentTokenId
              and token.usedAt is null
              and token.invalidatedAt is null
            """)
    int invalidateOtherActiveForUser(
            @Param("userId") Long userId,
            @Param("currentTokenId") Long currentTokenId,
            @Param("now") LocalDateTime now
    );

    @Modifying
    @Query("""
            delete from PasswordResetToken token
            where token.expiresAt < :cutoff
               or (token.usedAt is not null and token.usedAt < :cutoff)
               or (token.invalidatedAt is not null and token.invalidatedAt < :cutoff)
            """)
    int deleteExpiredOrConsumedBefore(@Param("cutoff") LocalDateTime cutoff);
}
