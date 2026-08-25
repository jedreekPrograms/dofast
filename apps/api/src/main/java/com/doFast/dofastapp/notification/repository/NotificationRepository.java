package com.doFast.dofastapp.notification.repository;

import com.doFast.dofastapp.notification.entity.Notification;
import com.doFast.dofastapp.user.entity.User;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.Optional;

public interface NotificationRepository extends JpaRepository<Notification, Long> {

    Page<Notification> findByRecipientOrderByCreatedAtDesc(User recipient, Pageable pageable);

    Page<Notification> findByRecipientAndReadAtIsNullOrderByCreatedAtDesc(User recipient, Pageable pageable);

    long countByRecipientAndReadAtIsNull(User recipient);

    Optional<Notification> findByIdAndRecipient(Long id, User recipient);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            update Notification n
            set n.readAt = :readAt
            where n.recipient = :recipient and n.readAt is null
            """)
    int markAllRead(@Param("recipient") User recipient, @Param("readAt") LocalDateTime readAt);
}
