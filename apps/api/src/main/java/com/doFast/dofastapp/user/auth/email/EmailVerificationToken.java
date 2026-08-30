package com.doFast.dofastapp.user.auth.email;

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

import java.time.LocalDateTime;

@Entity
@Table(name = "email_verification_tokens")
public class EmailVerificationToken {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(name = "token_hash", nullable = false, length = 64, unique = true)
    private String tokenHash;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "expires_at", nullable = false)
    private LocalDateTime expiresAt;

    @Column(name = "used_at")
    private LocalDateTime usedAt;

    @Column(name = "invalidated_at")
    private LocalDateTime invalidatedAt;

    protected EmailVerificationToken() {}

    public static EmailVerificationToken create(User user, String tokenHash, LocalDateTime now, LocalDateTime expiresAt) {
        EmailVerificationToken token = new EmailVerificationToken();
        token.user = user;
        token.tokenHash = tokenHash;
        token.createdAt = now;
        token.expiresAt = expiresAt;
        return token;
    }

    public boolean activeAt(LocalDateTime now) {
        return usedAt == null && invalidatedAt == null && expiresAt.isAfter(now);
    }

    public void markUsed(LocalDateTime now) { usedAt = now; }
    public void invalidate(LocalDateTime now) { if (invalidatedAt == null && usedAt == null) invalidatedAt = now; }
    public Long getId() { return id; }
    public User getUser() { return user; }
    public String getTokenHash() { return tokenHash; }
    public LocalDateTime getExpiresAt() { return expiresAt; }
    public LocalDateTime getUsedAt() { return usedAt; }
    public LocalDateTime getInvalidatedAt() { return invalidatedAt; }
}
