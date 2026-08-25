package com.doFast.dofastapp.chat.service;

import com.doFast.dofastapp.common.enums.JobStatus;
import com.doFast.dofastapp.common.exception.ConflictException;
import com.doFast.dofastapp.common.exception.ForbiddenOperationException;
import com.doFast.dofastapp.job.entity.Job;
import com.doFast.dofastapp.job.repository.JobRepository;
import com.doFast.dofastapp.user.entity.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ChatAccessServiceTest {

    @Mock private JobRepository jobRepository;

    private ChatAccessService accessService;
    private User owner;
    private User worker;

    @BeforeEach
    void setUp() {
        accessService = new ChatAccessService(jobRepository);
        owner = user(1L, "owner");
        worker = user(2L, "worker");
    }

    @Test
    void unrelatedUserCannotReadConversation() {
        Job job = job(JobStatus.IN_PROGRESS);
        when(jobRepository.findById(10L)).thenReturn(Optional.of(job));

        assertThrows(
                ForbiddenOperationException.class,
                () -> accessService.requireParticipant(10L, user(3L, "stranger"))
        );
    }

    @Test
    void completedConversationRemainsReadableButCannotReceiveNewMessages() {
        Job job = job(JobStatus.DONE);
        when(jobRepository.findById(10L)).thenReturn(Optional.of(job));

        assertEquals(job, accessService.requireParticipant(10L, owner));
        assertThrows(ConflictException.class, () -> accessService.requireSendable(10L, owner));
    }

    @Test
    void activeAcceptedConversationCanReceiveMessages() {
        Job job = job(JobStatus.IN_PROGRESS);
        when(jobRepository.findById(10L)).thenReturn(Optional.of(job));

        assertEquals(job, accessService.requireSendable(10L, worker));
        assertEquals(owner, accessService.otherParticipant(job, worker));
    }

    private Job job(JobStatus status) {
        Job job = new Job();
        ReflectionTestUtils.setField(job, "id", 10L);
        job.setStatus(status);
        job.setCreatedBy(owner);
        job.setTakenBy(worker);
        return job;
    }

    private User user(Long id, String nickname) {
        User user = new User(nickname + "@example.com", nickname);
        ReflectionTestUtils.setField(user, "id", id);
        return user;
    }
}
