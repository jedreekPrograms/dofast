package com.doFast.dofastapp.chat.repository;

import com.doFast.dofastapp.chat.entity.ChatMessage;
import com.doFast.dofastapp.job.entity.Job;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ChatMessageRepository extends JpaRepository<ChatMessage, Long> {
    List<ChatMessage> findByJobOrderByCreatedAtAsc(Job job);
}
