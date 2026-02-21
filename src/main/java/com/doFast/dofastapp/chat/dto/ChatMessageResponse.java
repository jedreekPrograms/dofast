package com.doFast.dofastapp.chat.dto;

import java.time.LocalDateTime;

public class ChatMessageResponse {

    private Long senderId;
    private String content;
    private LocalDateTime createdAt;

    public ChatMessageResponse(Long senderId, String content, LocalDateTime createdAt) {
        this.senderId = senderId;
        this.content = content;
        this.createdAt = createdAt;
    }

    public Long getSenderId() { return senderId; }
    public String getContent() { return content; }
    public LocalDateTime getCreatedAt() { return createdAt; }

}
