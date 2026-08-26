package com.doFast.dofastapp.user.auth.apple;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "apple_login_challenges")
public class AppleLoginChallenge {

    @Id
    private UUID id;

    @Column(name = "state_hash", nullable = false, length = 64)
    private String stateHash;

    @Column(name = "nonce_hash", nullable = false, length = 64)
    private String nonceHash;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @Column(name = "consumed_at")
    private Instant consumedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    public UUID getId() { return id; }
    public String getStateHash() { return stateHash; }
    public String getNonceHash() { return nonceHash; }
    public Instant getExpiresAt() { return expiresAt; }
    public Instant getConsumedAt() { return consumedAt; }
    public Instant getCreatedAt() { return createdAt; }

    public void setId(UUID id) { this.id = id; }
    public void setStateHash(String stateHash) { this.stateHash = stateHash; }
    public void setNonceHash(String nonceHash) { this.nonceHash = nonceHash; }
    public void setExpiresAt(Instant expiresAt) { this.expiresAt = expiresAt; }
    public void setConsumedAt(Instant consumedAt) { this.consumedAt = consumedAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
}