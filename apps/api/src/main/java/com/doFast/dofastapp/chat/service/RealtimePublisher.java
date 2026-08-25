package com.doFast.dofastapp.chat.service;

import com.doFast.dofastapp.chat.dto.ChatMessageResponse;
import com.doFast.dofastapp.notification.dto.NotificationResponse;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

@Service
public class RealtimePublisher {

    private final SimpMessagingTemplate messagingTemplate;

    public RealtimePublisher(SimpMessagingTemplate messagingTemplate) {
        this.messagingTemplate = messagingTemplate;
    }

    public void publishChat(Long jobId, ChatMessageResponse message) {
        afterCommit(() -> messagingTemplate.convertAndSend(
                "/topic/chat/" + jobId,
                message
        ));
    }

    public void publishNotification(String recipientEmail, NotificationResponse notification) {
        afterCommit(() -> messagingTemplate.convertAndSendToUser(
                recipientEmail,
                "/queue/notifications",
                notification
        ));
    }

    private void afterCommit(Runnable action) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            action.run();
            return;
        }

        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                action.run();
            }
        });
    }
}
