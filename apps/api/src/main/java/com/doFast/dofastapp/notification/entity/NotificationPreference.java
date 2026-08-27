package com.doFast.dofastapp.notification.entity;

import com.doFast.dofastapp.notification.enums.NotificationType;
import com.doFast.dofastapp.user.entity.User;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

@Entity
@Table(
        name = "notification_preferences",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_notification_preferences_user_type",
                columnNames = {"user_id", "notification_type"}
        )
)
public class NotificationPreference {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Enumerated(EnumType.STRING)
    @Column(name = "notification_type", nullable = false, length = 64)
    private NotificationType notificationType;

    public NotificationPreference() {}

    public NotificationPreference(User user, NotificationType notificationType) {
        this.user = user;
        this.notificationType = notificationType;
    }

    public Long getId() { return id; }
    public User getUser() { return user; }
    public NotificationType getNotificationType() { return notificationType; }
}
