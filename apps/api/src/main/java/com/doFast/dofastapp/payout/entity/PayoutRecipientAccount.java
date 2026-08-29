package com.doFast.dofastapp.payout.entity;

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
import jakarta.persistence.Version;

import java.time.LocalDateTime;

@Entity
@Table(
        name = "payout_recipient_accounts",
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_payout_recipient_user_provider", columnNames = {"user_id", "provider_code"}),
                @UniqueConstraint(name = "uk_payout_recipient_provider_account", columnNames = {"provider_code", "provider_account_id"})
        }
)
public class PayoutRecipientAccount {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Version
    @Column(nullable = false)
    private int version;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(name = "provider_code", nullable = false, length = 32)
    private String providerCode;

    @Column(name = "provider_account_id", nullable = false, length = 255)
    private String providerAccountId;

    @Column(name = "details_submitted", nullable = false)
    private boolean detailsSubmitted;

    @Column(name = "payouts_enabled", nullable = false)
    private boolean payoutsEnabled;

    @Column(name = "transfers_enabled", nullable = false)
    private boolean transfersEnabled;

    @Column(name = "requirements_due", nullable = false)
    private boolean requirementsDue = true;

    @Column(name = "last_synced_at")
    private LocalDateTime lastSyncedAt;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    public PayoutRecipientAccount() {}

    public void initialize(User user, String providerCode, String providerAccountId, LocalDateTime now) {
        if (user == null || providerCode == null || providerCode.isBlank() || providerAccountId == null || providerAccountId.isBlank() || now == null) {
            throw new IllegalArgumentException("Invalid payout recipient account initialization");
        }
        this.user = user;
        this.providerCode = providerCode;
        this.providerAccountId = providerAccountId;
        this.detailsSubmitted = false;
        this.payoutsEnabled = false;
        this.transfersEnabled = false;
        this.requirementsDue = true;
        this.createdAt = now;
        this.updatedAt = now;
    }

    public void synchronize(boolean detailsSubmitted, boolean payoutsEnabled, boolean transfersEnabled,
                            boolean requirementsDue, LocalDateTime now) {
        this.detailsSubmitted = detailsSubmitted;
        this.payoutsEnabled = payoutsEnabled;
        this.transfersEnabled = transfersEnabled;
        this.requirementsDue = requirementsDue;
        this.lastSyncedAt = now;
        this.updatedAt = now;
    }

    public boolean readyForPayout() {
        return detailsSubmitted && payoutsEnabled && transfersEnabled && !requirementsDue;
    }

    public Long getId() { return id; }
    public User getUser() { return user; }
    public String getProviderCode() { return providerCode; }
    public String getProviderAccountId() { return providerAccountId; }
    public boolean isDetailsSubmitted() { return detailsSubmitted; }
    public boolean isPayoutsEnabled() { return payoutsEnabled; }
    public boolean isTransfersEnabled() { return transfersEnabled; }
    public boolean isRequirementsDue() { return requirementsDue; }
    public LocalDateTime getLastSyncedAt() { return lastSyncedAt; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
}
