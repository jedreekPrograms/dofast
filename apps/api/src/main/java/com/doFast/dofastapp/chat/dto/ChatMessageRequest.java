package com.doFast.dofastapp.chat.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.UUID;

public class ChatMessageRequest {

    @NotNull
    private Long jobId;

    @NotBlank
    @Size(max = 4000)
    private String content;

    @NotNull
    private UUID clientMessageId;

    public Long getJobId() { return jobId; }
    public String getContent() { return content; }
    public UUID getClientMessageId() { return clientMessageId; }

    public void setJobId(Long jobId) { this.jobId = jobId; }
    public void setContent(String content) { this.content = content; }
    public void setClientMessageId(UUID clientMessageId) { this.clientMessageId = clientMessageId; }
}
