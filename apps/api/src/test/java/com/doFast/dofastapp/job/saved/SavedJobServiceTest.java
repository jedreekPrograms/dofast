package com.doFast.dofastapp.job.saved;

import com.doFast.dofastapp.common.dto.PageResponse;
import com.doFast.dofastapp.common.enums.JobStatus;
import com.doFast.dofastapp.common.exception.ConflictException;
import com.doFast.dofastapp.common.exception.ForbiddenOperationException;
import com.doFast.dofastapp.job.dto.JobResponse;
import com.doFast.dofastapp.job.entity.Job;
import com.doFast.dofastapp.job.repository.JobRepository;
import com.doFast.dofastapp.job.service.JobService;
import com.doFast.dofastapp.user.entity.User;
import com.doFast.dofastapp.user.service.UserBlockService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SavedJobServiceTest {

    @Mock private SavedJobRepository savedJobRepository;
    @Mock private JobRepository jobRepository;
    @Mock private JobService jobService;
    @Mock private UserBlockService userBlockService;

    private SavedJobService service;
    private User user;
    private Job job;

    @BeforeEach
    void setUp() {
        service = new SavedJobService(savedJobRepository, jobRepository, jobService, userBlockService);
        user = new User("user@example.com", "User");
        ReflectionTestUtils.setField(user, "id", 7L);
        job = new Job();
        ReflectionTestUtils.setField(job, "id", 11L);
        job.setStatus(JobStatus.OPEN);
    }

    @Test
    void saveIsIdempotentForOpenJob() {
        when(jobRepository.findById(11L)).thenReturn(Optional.of(job));
        when(savedJobRepository.existsByUser_IdAndJob_Id(7L, 11L)).thenReturn(false);

        service.save(11L, user);

        ArgumentCaptor<SavedJob> captor = ArgumentCaptor.forClass(SavedJob.class);
        verify(savedJobRepository).save(captor.capture());
        assertEquals(user, captor.getValue().getUser());
        assertEquals(job, captor.getValue().getJob());
    }

    @Test
    void saveDoesNotDuplicateExistingBookmark() {
        when(jobRepository.findById(11L)).thenReturn(Optional.of(job));
        when(savedJobRepository.existsByUser_IdAndJob_Id(7L, 11L)).thenReturn(true);

        service.save(11L, user);

        verify(savedJobRepository, never()).save(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void saveRejectsUnavailableJob() {
        job.setStatus(JobStatus.IN_PROGRESS);
        when(jobRepository.findById(11L)).thenReturn(Optional.of(job));

        assertThrows(ConflictException.class, () -> service.save(11L, user));
    }

    @Test
    void saveRejectsOwnJob() {
        job.setCreatedBy(user);
        when(jobRepository.findById(11L)).thenReturn(Optional.of(job));

        assertThrows(ForbiddenOperationException.class, () -> service.save(11L, user));
        verify(savedJobRepository, never()).save(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void saveRejectsJobAcrossUserBlock() {
        User owner = new User("owner@example.com", "Owner");
        ReflectionTestUtils.setField(owner, "id", 8L);
        job.setCreatedBy(owner);
        when(jobRepository.findById(11L)).thenReturn(Optional.of(job));
        when(userBlockService.isInteractionBlocked(owner, user)).thenReturn(true);

        assertThrows(ForbiddenOperationException.class, () -> service.save(11L, user));

        verify(savedJobRepository, never()).existsByUser_IdAndJob_Id(7L, 11L);
        verify(savedJobRepository, never()).save(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void statusesUseOneBatchedLookupAndDeduplicateRequestedIds() {
        when(savedJobRepository.findSavedJobIds(7L, List.of(11L, 12L, 13L)))
                .thenReturn(List.of(13L, 11L));

        SavedJobBatchStatusResponse response = service.statuses(List.of(11L, 12L, 11L, 13L), user);

        assertEquals(Set.of(11L, 13L), response.savedJobIds());
        verify(savedJobRepository).findSavedJobIds(7L, List.of(11L, 12L, 13L));
    }

    @Test
    void listRemovesUnavailableBookmarksBeforeReturningOpenJobs() {
        SavedJob savedJob = new SavedJob(user, job);
        PageRequest request = PageRequest.of(0, 20);
        when(savedJobRepository.findByUserAndJobStatus(7L, JobStatus.OPEN, request))
                .thenReturn(new PageImpl<>(List.of(savedJob), request, 1));
        JobResponse response = org.mockito.Mockito.mock(JobResponse.class);
        when(jobService.getJob(11L)).thenReturn(response);

        PageResponse<JobResponse> result = service.list(user, 0, 20);

        verify(savedJobRepository).deleteByUserAndJobStatusNot(7L, JobStatus.OPEN);
        assertEquals(List.of(response), result.content());
        assertEquals(1, result.totalElements());
        assertEquals(0, result.page());
    }
}
