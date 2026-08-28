package com.doFast.dofastapp.job.service;

import com.doFast.dofastapp.common.exception.ResourceNotFoundException;
import com.doFast.dofastapp.job.entity.Job;
import com.doFast.dofastapp.job.repository.JobRepository;
import com.doFast.dofastapp.user.entity.User;
import com.doFast.dofastapp.user.service.UserBlockService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class JobVisibilityServiceTest {

    @Mock private JobRepository jobRepository;
    @Mock private UserBlockService userBlockService;

    private JobVisibilityService service;
    private User owner;
    private User worker;
    private User stranger;
    private Job job;

    @BeforeEach
    void setUp() {
        service = new JobVisibilityService(jobRepository, userBlockService);
        owner = user(1L);
        worker = user(2L);
        stranger = user(3L);
        job = new Job();
        ReflectionTestUtils.setField(job, "id", 10L);
        job.setCreatedBy(owner);
        ReflectionTestUtils.setField(job, "takenBy", worker);
    }

    @Test
    void unauthenticatedPublicReadKeepsExistingBehavior() {
        assertDoesNotThrow(() -> service.assertCanViewPublicDetail(10L, null));
        verifyNoInteractions(jobRepository, userBlockService);
    }

    @Test
    void jobParticipantsRetainDetailAccessAfterBlocking() {
        when(jobRepository.findById(10L)).thenReturn(Optional.of(job));

        assertDoesNotThrow(() -> service.assertCanViewPublicDetail(10L, owner));
        assertDoesNotThrow(() -> service.assertCanViewPublicDetail(10L, worker));
        verifyNoInteractions(userBlockService);
    }

    @Test
    void blockedNonParticipantReceivesNeutralNotFound() {
        when(jobRepository.findById(10L)).thenReturn(Optional.of(job));
        when(userBlockService.isInteractionBlocked(owner, stranger)).thenReturn(true);

        assertThrows(ResourceNotFoundException.class,
                () -> service.assertCanViewPublicDetail(10L, stranger));
    }

    @Test
    void unblockedNonParticipantCanReadPublicDetail() {
        when(jobRepository.findById(10L)).thenReturn(Optional.of(job));
        when(userBlockService.isInteractionBlocked(owner, stranger)).thenReturn(false);

        assertDoesNotThrow(() -> service.assertCanViewPublicDetail(10L, stranger));
    }

    private User user(Long id) {
        User user = new User();
        ReflectionTestUtils.setField(user, "id", id);
        return user;
    }
}
