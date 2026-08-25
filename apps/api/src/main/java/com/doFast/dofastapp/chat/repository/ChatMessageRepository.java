package com.doFast.dofastapp.chat.repository;

import com.doFast.dofastapp.chat.entity.ChatMessage;
import com.doFast.dofastapp.job.entity.Job;
import com.doFast.dofastapp.user.entity.User;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ChatMessageRepository extends JpaRepository<ChatMessage, Long> {

    List<ChatMessage> findByJobOrderByIdDesc(Job job, Pageable pageable);

    List<ChatMessage> findByJobAndIdLessThanOrderByIdDesc(Job job, Long beforeId, Pageable pageable);

    Optional<ChatMessage> findFirstByJobOrderByIdDesc(Job job);

    Optional<ChatMessage> findByIdAndJob(Long id, Job job);

    Optional<ChatMessage> findBySenderAndClientMessageId(User sender, UUID clientMessageId);

    long countByJobAndSenderNotAndIdGreaterThan(Job job, User sender, Long id);
}
