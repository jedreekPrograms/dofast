package com.doFast.dofastapp.chat.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.UUID;

public record SendChatMessageRequest(
        @NotBlank @Size(max = 4000) String content,
        @NotNull UUID clientMessageId
) {}
