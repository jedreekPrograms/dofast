package com.doFast.dofastapp.user.entity;

import com.doFast.dofastapp.user.enums.UserRole;
import com.doFast.dofastapp.user.enums.UserStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import java.time.LocalDateTime;

@Entity
@Table(
        name = "users",
        uniqueConstraints = @UniqueConstraint(name = "uk_users_email", columnNames = "email")
)
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 320)
    private String email;

    @Column(nullable = false, length = 80)
    private String nickname;

    @Column(length = 600)
    private String bio;

    @Column(name = "public_location", length = 120)
    private String publicLocation;

    @Column(nullable = false, length = 255)
    private String password;

    @Column(name = "password_login_enabled", nullable = false)
    private boolean passwordLoginEnabled = true;

    @Column(name = "auth_version", nullable = false)
    private long authVersion = 0;

    @Column(name = "email_verified_at")
    private LocalDateTime emailVerifiedAt;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private UserRole role = UserRole.USER;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private UserStatus status = UserStatus.ACTIVE;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    public User() {}

    public User(String email, String nickname) {
        this.email = email;
        this.nickname = nickname;
    }

    @PrePersist
    void onCreate() {
        LocalDateTime now = LocalDateTime.now();
        createdAt = now;
        updatedAt = now;
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = LocalDateTime.now();
    }

    public void incrementAuthVersion() {
        if (authVersion == Long.MAX_VALUE) {
            throw new IllegalStateException("Authentication version exhausted");
        }
        authVersion++;
    }

    public boolean isEmailVerified() { return emailVerifiedAt != null; }
    public void markEmailVerified(LocalDateTime verifiedAt) {
        if (emailVerifiedAt == null) emailVerifiedAt = verifiedAt;
    }

    public Long getId() { return id; }
    public String getEmail() { return email; }
    public String getPassword() { return password; }
    public boolean isPasswordLoginEnabled() { return passwordLoginEnabled; }
    public long getAuthVersion() { return authVersion; }
    public LocalDateTime getEmailVerifiedAt() { return emailVerifiedAt; }
    public String getNickname() { return nickname; }
    public String getBio() { return bio; }
    public String getPublicLocation() { return publicLocation; }
    public UserRole getRole() { return role; }
    public UserStatus getStatus() { return status; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }

    public void setEmail(String email) { this.email = email; }
    public void setNickname(String nickname) { this.nickname = nickname; }
    public void setBio(String bio) { this.bio = bio; }
    public void setPublicLocation(String publicLocation) { this.publicLocation = publicLocation; }
    public void setPassword(String password) { this.password = password; }
    public void setPasswordLoginEnabled(boolean passwordLoginEnabled) { this.passwordLoginEnabled = passwordLoginEnabled; }
    public void setEmailVerifiedAt(LocalDateTime emailVerifiedAt) { this.emailVerifiedAt = emailVerifiedAt; }
    public void setRole(UserRole role) { this.role = role; }
    public void setStatus(UserStatus status) { this.status = status; }
}
