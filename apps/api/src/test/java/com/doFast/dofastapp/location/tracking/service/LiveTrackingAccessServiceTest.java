package com.doFast.dofastapp.location.tracking.service;

import com.doFast.dofastapp.common.enums.JobStatus;
import com.doFast.dofastapp.common.exception.ConflictException;
import com.doFast.dofastapp.common.exception.ForbiddenOperationException;
import com.doFast.dofastapp.common.exception.ResourceNotFoundException;
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
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class LiveTrackingAccessServiceTest {

    @Mock private JobRepository jobRepository;

    private LiveTrackingAccessService accessService;
    private User owner;
    private User worker;

    @BeforeEach
    void setUp() {
        accessService = new LiveTrackingAccessService(jobRepository);
        owner = user(1L, "owner");
        worker = user(2L, "worker");
    }

    @Test
    void outsiderCannotDistinguishActiveTrackingFromMissingJob() {
        when(jobRepository.findParticipantById(10L, 3L)).thenReturn(Optional.empty());

        assertThrows(
                ResourceNotFoundException.class,
                () -> accessService.requireViewer(10L, user(3L, "stranger"))
        );
    }

    @Test
    void outsiderCannotProbeTrackingLifecycleThroughWorkerEndpoint() {
        when(jobRepository.findParticipantById(10L, 3L)).thenReturn(Optional.empty());

        assertThrows(
                ResourceNotFoundException.class,
                () -> accessService.requireWorker(10L, user(3L, "stranger"))
        );
    }

    @Test
    void missingAuthenticatedIdentityFailsClosedBeforeRepositoryAccess() {
        assertThrows(ResourceNotFoundException.class, () -> accessService.requireViewer(10L, null));
        assertThrows(ResourceNotFoundException.class, () -> accessService.requireWorker(10L, user(null, "anonymous")));
        verifyNoInteractions(jobRepository);
    }

    @Test
    void ownerCanViewActiveTrackingButCannotPublishWorkerLocation() {
        Job job = job(JobStatus.IN_PROGRESS);
        when(jobRepository.findParticipantById(10L, owner.getId())).thenReturn(Optional.of(job));

        assertEquals(job, accessService.requireViewer(10L, owner));
        assertThrows(ForbiddenOperationException.class, () -> accessService.requireWorker(10L, owner));
    }

    @Test
    void assignedWorkerCanViewAndPublishActiveTracking() {
        Job job = job(JobStatus.COMPLETION_REQUESTED);
        when(jobRepository.findParticipantById(10L, worker.getId())).thenReturn(Optional.of(job));

        assertEquals(job, accessService.requireViewer(10L, worker));
        assertEquals(job, accessService.requireWorker(10L, worker));
    }

    @Test
    void participantStillReceivesLifecycleConflictWhenTrackingIsInactive() {
        Job job = job(JobStatus.DONE);
        when(jobRepository.findParticipantById(10L, owner.getId())).thenReturn(Optional.of(job));

        assertThrows(ConflictException.class, () -> accessService.requireViewer(10L, owner));
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
