package com.doFast.dofastapp.chat.dto;

import java.time.LocalDateTime;
import java.util.UUID;

public record ChatMessageResponse(
        Long id,
        Long jobId,
        Long senderId,
        String senderNickname,
        String content,
        UUID clientMessageId,
        LocalDateTime createdAt
) {}
