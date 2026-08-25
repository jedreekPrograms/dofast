package com.doFast.dofastapp.chat.dto;


public class ChatMessageRequest {

    private Long jobId;
    private Long senderId;
    private String content;

    public Long getJobId() {
        return jobId;
    }

    public Long getSenderId() {
        return senderId;
    }

    public String getContent() {
        return content;
    }

    public void setJobId(Long jobId) {
        this.jobId = jobId;
    }

    public void setSenderId(Long senderId) {
        this.senderId = senderId;
    }

    public void setContent(String content) {
        this.content = content;
    }
}
