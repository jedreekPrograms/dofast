package com.doFast.dofastapp.notification.repository;

import com.doFast.dofastapp.notification.entity.NotificationPreference;
import com.doFast.dofastapp.notification.enums.NotificationType;
import com.doFast.dofastapp.user.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import java.util.List;

public interface NotificationPreferenceRepository extends JpaRepository<NotificationPreference, Long> {
    boolean existsByUserAndNotificationType(User user, NotificationType notificationType);
    List<NotificationPreference> findAllByUser(User user);

    @Modifying
    @Query("delete from NotificationPreference preference where preference.user = :user")
    int deleteAllForUser(User user);
}
