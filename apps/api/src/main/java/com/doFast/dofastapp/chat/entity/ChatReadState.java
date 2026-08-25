package com.doFast.dofastapp.chat.entity;

import com.doFast.dofastapp.job.entity.Job;
import com.doFast.dofastapp.user.entity.User;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import java.time.LocalDateTime;

@Entity
@Table(
        name = "chat_read_states",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_chat_read_states_job_user",
                columnNames = {"job_id", "user_id"}
        )
)
public class ChatReadState {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false)
    @JoinColumn(name = "job_id", nullable = false)
    private Job job;

    @ManyToOne(optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @ManyToOne
    @JoinColumn(name = "last_read_message_id")
    private ChatMessage lastReadMessage;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    public ChatReadState() {}

    public void advanceTo(ChatMessage message, LocalDateTime at) {
        if (lastReadMessage == null || message.getId() > lastReadMessage.getId()) {
            lastReadMessage = message;
        }
        updatedAt = at;
    }

    public Long getId() { return id; }
    public Job getJob() { return job; }
    public User getUser() { return user; }
    public ChatMessage getLastReadMessage() { return lastReadMessage; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }

    public void setJob(Job job) { this.job = job; }
    public void setUser(User user) { this.user = user; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
}
