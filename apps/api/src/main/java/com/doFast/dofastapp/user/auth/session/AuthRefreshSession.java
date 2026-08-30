package com.doFast.dofastapp.user.auth.session;

import com.doFast.dofastapp.user.entity.User;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(
        name = "auth_refresh_sessions",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_auth_refresh_sessions_token_hash",
                columnNames = "token_hash"
        )
)
public class AuthRefreshSession {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(name = "family_id", nullable = false, columnDefinition = "uuid")
    private UUID familyId;

    @Column(name = "token_hash", nullable = false, length = 64)
    private String tokenHash;

    @Column(name = "csrf_hash", nullable = false, length = 64)
    private String csrfHash;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "expires_at", nullable = false)
    private LocalDateTime expiresAt;

    @Column(name = "last_used_at")
    private LocalDateTime lastUsedAt;

    @Column(name = "revoked_at")
    private LocalDateTime revokedAt;

    @Column(name = "revocation_reason", length = 32)
    private String revocationReason;

    protected AuthRefreshSession() {}

    public static AuthRefreshSession create(
            User user,
            UUID familyId,
            String tokenHash,
            String csrfHash,
            LocalDateTime now,
            LocalDateTime expiresAt
    ) {
        if (user == null || familyId == null || now == null || expiresAt == null || !expiresAt.isAfter(now)) {
            throw new IllegalArgumentException("Invalid refresh session lifecycle data");
        }
        AuthRefreshSession session = new AuthRefreshSession();
        session.user = user;
        session.familyId = familyId;
        session.tokenHash = requireHash(tokenHash, "refresh token hash");
        session.csrfHash = requireHash(csrfHash, "CSRF token hash");
        session.createdAt = now;
        session.expiresAt = expiresAt;
        return session;
    }

    public boolean activeAt(LocalDateTime now) {
        return revokedAt == null && expiresAt.isAfter(now);
    }

    public boolean revoked() {
        return revokedAt != null;
    }

    public boolean expiredAt(LocalDateTime now) {
        return !expiresAt.isAfter(now);
    }

    public void markUsed(LocalDateTime now) {
        if (now == null) throw new IllegalArgumentException("Refresh session use time is required");
        lastUsedAt = now;
    }

    public void revoke(String reason, LocalDateTime now) {
        if (reason == null || reason.isBlank() || reason.length() > 32 || now == null) {
            throw new IllegalArgumentException("Invalid refresh session revocation");
        }
        if (revokedAt != null) return;
        revokedAt = now;
        revocationReason = reason;
    }

    private static String requireHash(String value, String label) {
        if (value == null || value.length() != 64) {
            throw new IllegalArgumentException("Invalid " + label);
        }
        return value;
    }

    public Long getId() { return id; }
    public User getUser() { return user; }
    public UUID getFamilyId() { return familyId; }
    public String getTokenHash() { return tokenHash; }
    public String getCsrfHash() { return csrfHash; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public LocalDateTime getExpiresAt() { return expiresAt; }
    public LocalDateTime getLastUsedAt() { return lastUsedAt; }
    public LocalDateTime getRevokedAt() { return revokedAt; }
    public String getRevocationReason() { return revocationReason; }
}
