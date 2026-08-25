package com.doFast.dofastapp.chat.dto;

import jakarta.validation.constraints.NotNull;

public record MarkChatReadRequest(@NotNull Long lastMessageId) {}
