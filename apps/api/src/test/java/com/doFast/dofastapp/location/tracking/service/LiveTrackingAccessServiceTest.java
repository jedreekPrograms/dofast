package com.doFast.dofastapp.location.tracking.service;

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

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class LiveTrackingAccessServiceTest {

    @Mock
    private JobRepository jobRepository;

    private LiveTrackingAccessService accessService;

    @BeforeEach
    void setUp() {
        accessService = new LiveTrackingAccessService(jobRepository);
    }

    @Test
    void outsiderCannotDistinguishTrackingJobFromMissingJob() {
        User outsider = user(3L);
        when(jobRepository.findParticipantById(10L, 3L)).thenReturn(Optional.empty());

        assertThrows(ForbiddenOperationException.class, () -> accessService.requireViewer(10L, outsider));
        assertThrows(ForbiddenOperationException.class, () -> accessService.requireWorker(10L, outsider));
    }

    @Test
    void missingAuthenticatedIdentityFailsClosedBeforeRepositoryAccess() {
        assertThrows(ForbiddenOperationException.class, () -> accessService.requireViewer(10L, user(null)));
        verifyNoInteractions(jobRepository);
    }

    @Test
    void participantStillReceivesLifecycleConflictWhenTrackingIsInactive() {
        User owner = user(1L);
        Job job = job(JobStatus.DONE);
        when(jobRepository.findParticipantById(10L, 1L)).thenReturn(Optional.of(job));

        assertThrows(ConflictException.class, () -> accessService.requireViewer(10L, owner));
    }

    @Test
    void ownerCanViewActiveTracking() {
        User owner = user(1L);
        Job job = job(JobStatus.IN_PROGRESS);
        when(jobRepository.findParticipantById(10L, 1L)).thenReturn(Optional.of(job));

        assertEquals(job, accessService.requireViewer(10L, owner));
    }

    @Test
    void assignedWorkerCanPublishActiveTracking() {
        User worker = user(2L);
        Job job = job(JobStatus.COMPLETION_REQUESTED);
        when(job.getTakenBy()).thenReturn(worker);
        when(jobRepository.findParticipantById(10L, 2L)).thenReturn(Optional.of(job));

        assertEquals(job, accessService.requireWorker(10L, worker));
    }

    @Test
    void ownerStillCannotPublishWorkerLocation() {
        User owner = user(1L);
        User worker = user(2L);
        Job job = job(JobStatus.IN_PROGRESS);
        when(job.getTakenBy()).thenReturn(worker);
        when(jobRepository.findParticipantById(10L, 1L)).thenReturn(Optional.of(job));

        assertThrows(ForbiddenOperationException.class, () -> accessService.requireWorker(10L, owner));
    }

    private User user(Long id) {
        User user = mock(User.class);
        when(user.getId()).thenReturn(id);
        return user;
    }

    private Job job(JobStatus status) {
        Job job = mock(Job.class);
        when(job.getStatus()).thenReturn(status);
        return job;
    }
}
