package com.doFast.dofastapp.user.auth.session;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

public interface AuthRefreshSessionRepository extends JpaRepository<AuthRefreshSession, Long> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @EntityGraph(attributePaths = "user")
    @Query("select session from AuthRefreshSession session where session.tokenHash = :tokenHash")
    Optional<AuthRefreshSession> findByTokenHashForUpdate(@Param("tokenHash") String tokenHash);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            update AuthRefreshSession session
            set session.revokedAt = :now,
                session.revocationReason = :reason
            where session.familyId = :familyId
              and session.revokedAt is null
            """)
    int revokeActiveFamily(
            @Param("familyId") UUID familyId,
            @Param("reason") String reason,
            @Param("now") LocalDateTime now
    );

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            update AuthRefreshSession session
            set session.revokedAt = :now,
                session.revocationReason = :reason
            where session.user.id = :userId
              and session.revokedAt is null
            """)
    int revokeAllActiveForUser(
            @Param("userId") Long userId,
            @Param("reason") String reason,
            @Param("now") LocalDateTime now
    );

    @Modifying
    @Query("""
            delete from AuthRefreshSession session
            where session.expiresAt < :cutoff
               or (session.revokedAt is not null and session.revokedAt < :cutoff)
            """)
    int deleteExpiredOrOldRevoked(@Param("cutoff") LocalDateTime cutoff);
}
