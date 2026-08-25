package com.doFast.dofastapp.chat.service;

import com.doFast.dofastapp.chat.dto.ChatMessageResponse;
import com.doFast.dofastapp.chat.entity.ChatMessage;
import com.doFast.dofastapp.chat.repository.ChatMessageRepository;
import com.doFast.dofastapp.common.exception.BusinessException;
import com.doFast.dofastapp.job.entity.Job;
import com.doFast.dofastapp.job.repository.JobRepository;
import com.doFast.dofastapp.user.entity.User;
import com.doFast.dofastapp.user.repository.UserRepository;
import org.springframework.stereotype.Service;

@Service
public class ChatService {

    private final ChatMessageRepository chatRepo;
    private final JobRepository jobRepository;
    private final UserRepository userRepository;

    public ChatService(ChatMessageRepository chatRepo, JobRepository jobRepository, UserRepository userRepository) {
        this.chatRepo = chatRepo;
        this.jobRepository = jobRepository;
        this.userRepository = userRepository;
    }

    public ChatMessageResponse sendMessage(Long jobId, Long senderId, String content) {

        User sender = userRepository.findById(senderId)
                .orElseThrow(() -> new BusinessException("Użytkownik nie istnieje"));

        Job job = jobRepository.findById(jobId)
                .orElseThrow(() -> new BusinessException("Zlecenie nie istnieje"));

        boolean allowed =
                job.getCreatedBy().getId().equals(sender.getId()) ||
                        (job.getTakenBy() != null && job.getTakenBy().getId().equals(sender.getId()));

        if (!allowed) {
            throw new BusinessException("Nie masz dostępu do tego czatu");
        }

        ChatMessage msg = new ChatMessage(job, sender, content);
        ChatMessage saved = chatRepo.save(msg);

        return new ChatMessageResponse(
                sender.getId(),
                saved.getContent(),
                saved.getCreatedAt()
        );
    }
}

