package com.doFast.dofastapp.user.auth.password;

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

@Entity
@Table(
        name = "auth_password_reset_tokens",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_auth_password_reset_tokens_hash",
                columnNames = "token_hash"
        )
)
public class PasswordResetToken {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(name = "token_hash", nullable = false, length = 64)
    private String tokenHash;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "expires_at", nullable = false)
    private LocalDateTime expiresAt;

    @Column(name = "used_at")
    private LocalDateTime usedAt;

    @Column(name = "invalidated_at")
    private LocalDateTime invalidatedAt;

    protected PasswordResetToken() {}

    public static PasswordResetToken create(
            User user,
            String tokenHash,
            LocalDateTime now,
            LocalDateTime expiresAt
    ) {
        if (user == null || now == null || expiresAt == null || !expiresAt.isAfter(now)) {
            throw new IllegalArgumentException("Invalid password reset token lifecycle");
        }
        if (tokenHash == null || tokenHash.length() != 64) {
            throw new IllegalArgumentException("Password reset token hash must be SHA-256 hex");
        }
        PasswordResetToken token = new PasswordResetToken();
        token.user = user;
        token.tokenHash = tokenHash;
        token.createdAt = now;
        token.expiresAt = expiresAt;
        return token;
    }

    public boolean activeAt(LocalDateTime now) {
        return usedAt == null && invalidatedAt == null && expiresAt.isAfter(now);
    }

    public void markUsed(LocalDateTime now) {
        if (now == null || usedAt != null || invalidatedAt != null) {
            throw new IllegalStateException("Password reset token is not usable");
        }
        usedAt = now;
    }

    public void invalidate(LocalDateTime now) {
        if (now == null) throw new IllegalArgumentException("Invalidation time is required");
        if (usedAt != null || invalidatedAt != null) return;
        invalidatedAt = now;
    }

    public Long getId() { return id; }
    public User getUser() { return user; }
    public String getTokenHash() { return tokenHash; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public LocalDateTime getExpiresAt() { return expiresAt; }
    public LocalDateTime getUsedAt() { return usedAt; }
    public LocalDateTime getInvalidatedAt() { return invalidatedAt; }
}
