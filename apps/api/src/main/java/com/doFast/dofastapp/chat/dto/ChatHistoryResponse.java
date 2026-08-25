package com.doFast.dofastapp.chat.dto;

import java.util.List;

public record ChatHistoryResponse(
        List<ChatMessageResponse> messages,
        Long nextBeforeId,
        boolean hasMore
) {}
