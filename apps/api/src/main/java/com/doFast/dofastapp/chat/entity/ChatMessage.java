package com.doFast.dofastapp.chat.entity;

import com.doFast.dofastapp.job.entity.Job;
import com.doFast.dofastapp.user.entity.User;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(
        name = "chat_messages",
        indexes = {
                @Index(name = "idx_chat_messages_job_created", columnList = "job_id,created_at"),
                @Index(name = "idx_chat_messages_sender", columnList = "sender_id"),
                @Index(name = "idx_chat_messages_job_id_desc", columnList = "job_id,id")
        }
)
public class ChatMessage {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false)
    @JoinColumn(name = "job_id", nullable = false)
    private Job job;

    @ManyToOne(optional = false)
    @JoinColumn(name = "sender_id", nullable = false)
    private User sender;

    @Column(nullable = false, length = 4000)
    private String content;

    @Column(name = "client_message_id")
    private UUID clientMessageId;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    public ChatMessage() {}

    public ChatMessage(Job job, User sender, String content, UUID clientMessageId) {
        this.job = job;
        this.sender = sender;
        this.content = content;
        this.clientMessageId = clientMessageId;
        this.createdAt = LocalDateTime.now();
    }

    public Long getId() { return id; }
    public Job getJob() { return job; }
    public User getSender() { return sender; }
    public String getContent() { return content; }
    public UUID getClientMessageId() { return clientMessageId; }
    public LocalDateTime getCreatedAt() { return createdAt; }
}
