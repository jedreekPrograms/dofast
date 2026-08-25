package com.doFast.dofastapp.chat.dto;

import com.doFast.dofastapp.common.enums.JobStatus;

public record ChatConversationResponse(
        Long jobId,
        String jobTitle,
        JobStatus jobStatus,
        Long otherUserId,
        String otherUserNickname,
        ChatMessageResponse lastMessage,
        long unreadCount
) {}
