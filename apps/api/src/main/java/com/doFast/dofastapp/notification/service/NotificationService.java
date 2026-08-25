package com.doFast.dofastapp.notification.service;

import com.doFast.dofastapp.chat.service.RealtimePublisher;
import com.doFast.dofastapp.common.dto.PageResponse;
import com.doFast.dofastapp.common.exception.ResourceNotFoundException;
import com.doFast.dofastapp.dispute.entity.Dispute;
import com.doFast.dofastapp.job.entity.Job;
import com.doFast.dofastapp.notification.dto.NotificationResponse;
import com.doFast.dofastapp.notification.dto.UnreadNotificationCountResponse;
import com.doFast.dofastapp.notification.entity.Notification;
import com.doFast.dofastapp.notification.enums.NotificationType;
import com.doFast.dofastapp.notification.repository.NotificationRepository;
import com.doFast.dofastapp.user.entity.User;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Service
@Transactional(readOnly = true)
public class NotificationService {

    private final NotificationRepository notificationRepository;
    private final RealtimePublisher realtimePublisher;

    public NotificationService(
            NotificationRepository notificationRepository,
            RealtimePublisher realtimePublisher
    ) {
        this.notificationRepository = notificationRepository;
        this.realtimePublisher = realtimePublisher;
    }

    public PageResponse<NotificationResponse> getNotifications(
            User user,
            boolean unreadOnly,
            int page,
            int size
    ) {
        PageRequest pageable = PageRequest.of(page, size);
        Page<Notification> notifications = unreadOnly
                ? notificationRepository.findByRecipientAndReadAtIsNullOrderByCreatedAtDesc(user, pageable)
                : notificationRepository.findByRecipientOrderByCreatedAtDesc(user, pageable);

        List<NotificationResponse> content = notifications.getContent()
                .stream()
                .map(this::toResponse)
                .toList();

        return PageResponse.from(notifications, content);
    }

    public UnreadNotificationCountResponse getUnreadCount(User user) {
        return new UnreadNotificationCountResponse(
                notificationRepository.countByRecipientAndReadAtIsNull(user)
        );
    }

    @Transactional
    public NotificationResponse markRead(Long notificationId, User user) {
        Notification notification = notificationRepository.findByIdAndRecipient(notificationId, user)
                .orElseThrow(() -> new ResourceNotFoundException("Powiadomienie nie istnieje"));

        notification.markRead(LocalDateTime.now());
        return toResponse(notificationRepository.save(notification));
    }

    @Transactional
    public int markAllRead(User user) {
        return notificationRepository.markAllRead(user, LocalDateTime.now());
    }

    @Transactional
    public NotificationResponse notify(
            User recipient,
            NotificationType type,
            String title,
            String body,
            Job job,
            Dispute dispute
    ) {
        if (recipient == null) {
            throw new IllegalArgumentException("Notification recipient cannot be null");
        }

        Notification notification = new Notification();
        notification.setRecipient(recipient);
        notification.setType(type);
        notification.setTitle(title);
        notification.setBody(body);
        notification.setJob(job);
        notification.setDispute(dispute);
        notification.setCreatedAt(LocalDateTime.now());

        NotificationResponse response = toResponse(notificationRepository.save(notification));
        realtimePublisher.publishNotification(recipient.getEmail(), response);
        return response;
    }

    private NotificationResponse toResponse(Notification notification) {
        return new NotificationResponse(
                notification.getId(),
                notification.getType(),
                notification.getTitle(),
                notification.getBody(),
                notification.getJob() != null ? notification.getJob().getId() : null,
                notification.getDispute() != null ? notification.getDispute().getId() : null,
                notification.isRead(),
                notification.getCreatedAt(),
                notification.getReadAt()
        );
    }
}
