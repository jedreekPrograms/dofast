package com.doFast.dofastapp.notification.service;

import com.doFast.dofastapp.chat.service.RealtimePublisher;
import com.doFast.dofastapp.job.entity.Job;
import com.doFast.dofastapp.notification.entity.Notification;
import com.doFast.dofastapp.notification.enums.NotificationType;
import com.doFast.dofastapp.notification.repository.NotificationRepository;
import com.doFast.dofastapp.user.entity.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class NotificationServiceTest {

    @Mock private NotificationRepository notificationRepository;
    @Mock private RealtimePublisher realtimePublisher;

    private NotificationService notificationService;
    private User user;

    @BeforeEach
    void setUp() {
        notificationService = new NotificationService(notificationRepository, realtimePublisher);
        user = new User("user@example.com", "user");
        ReflectionTestUtils.setField(user, "id", 1L);
    }

    @Test
    void notificationIsPersistedBeforeRealtimeDeliveryIsScheduled() {
        when(notificationRepository.save(any(Notification.class))).thenAnswer(invocation -> {
            Notification notification = invocation.getArgument(0);
            ReflectionTestUtils.setField(notification, "id", 10L);
            return notification;
        });

        var response = notificationService.notify(
                user,
                NotificationType.JOB_ACCEPTED,
                "Zlecenie przyjęte",
                "Wykonawca przyjął zlecenie",
                null,
                null
        );

        assertEquals(10L, response.id());
        assertEquals(NotificationType.JOB_ACCEPTED, response.type());
        verify(realtimePublisher).publishNotification("user@example.com", response);
    }

    @Test
    void userCanOnlyMarkTheirOwnNotificationRead() {
        Notification notification = new Notification();
        ReflectionTestUtils.setField(notification, "id", 10L);
        notification.setRecipient(user);
        notification.setType(NotificationType.CHAT_MESSAGE);
        notification.setTitle("Nowa wiadomość");
        notification.setBody("Treść");
        notification.setCreatedAt(java.time.LocalDateTime.now());
        when(notificationRepository.findByIdAndRecipient(10L, user)).thenReturn(Optional.of(notification));
        when(notificationRepository.save(notification)).thenReturn(notification);

        var response = notificationService.markRead(10L, user);

        assertTrue(response.read());
        verify(notificationRepository).findByIdAndRecipient(10L, user);
    }

    @Test
    void unreadCountComesFromRecipientScopedQuery() {
        when(notificationRepository.countByRecipientAndReadAtIsNull(user)).thenReturn(7L);

        assertEquals(7L, notificationService.getUnreadCount(user).unreadCount());
    }
}
