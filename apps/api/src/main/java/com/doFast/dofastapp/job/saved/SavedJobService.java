package com.doFast.dofastapp.job.saved;

import com.doFast.dofastapp.common.dto.PageResponse;
import com.doFast.dofastapp.common.enums.JobStatus;
import com.doFast.dofastapp.common.exception.ConflictException;
import com.doFast.dofastapp.common.exception.ForbiddenOperationException;
import com.doFast.dofastapp.common.exception.ResourceNotFoundException;
import com.doFast.dofastapp.job.dto.JobResponse;
import com.doFast.dofastapp.job.entity.Job;
import com.doFast.dofastapp.job.repository.JobRepository;
import com.doFast.dofastapp.job.service.JobService;
import com.doFast.dofastapp.user.entity.User;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashSet;
import java.util.List;

@Service
@Transactional(readOnly = true)
public class SavedJobService {

    private final SavedJobRepository savedJobRepository;
    private final JobRepository jobRepository;
    private final JobService jobService;

    public SavedJobService(
            SavedJobRepository savedJobRepository,
            JobRepository jobRepository,
            JobService jobService
    ) {
        this.savedJobRepository = savedJobRepository;
        this.jobRepository = jobRepository;
        this.jobService = jobService;
    }

    @Transactional
    public void save(Long jobId, User user) {
        Job job = jobRepository.findById(jobId)
                .orElseThrow(() -> new ResourceNotFoundException("Zlecenie nie istnieje"));
        if (job.getStatus() != JobStatus.OPEN) {
            throw new ConflictException("Możesz zapisać tylko dostępne zlecenie");
        }
        if (sameUser(job.getCreatedBy(), user)) {
            throw new ForbiddenOperationException("Nie możesz zapisać własnego zlecenia");
        }
        if (!savedJobRepository.existsByUser_IdAndJob_Id(user.getId(), jobId)) {
            savedJobRepository.save(new SavedJob(user, job));
        }
    }

    @Transactional
    public void remove(Long jobId, User user) {
        savedJobRepository.deleteByUser_IdAndJob_Id(user.getId(), jobId);
    }

    public SavedJobStatusResponse status(Long jobId, User user) {
        return new SavedJobStatusResponse(savedJobRepository.existsByUser_IdAndJob_Id(user.getId(), jobId));
    }

    public SavedJobBatchStatusResponse statuses(List<Long> jobIds, User user) {
        List<Long> uniqueIds = jobIds.stream().distinct().toList();
        List<Long> savedIds = savedJobRepository.findSavedJobIds(user.getId(), uniqueIds);
        return new SavedJobBatchStatusResponse(new LinkedHashSet<>(savedIds));
    }

    @Transactional
    public PageResponse<JobResponse> list(User user, int page, int size) {
        savedJobRepository.deleteByUserAndJobStatusNot(user.getId(), JobStatus.OPEN);
        Page<SavedJob> saved = savedJobRepository.findByUserAndJobStatus(
                user.getId(),
                JobStatus.OPEN,
                PageRequest.of(page, size)
        );
        return PageResponse.from(
                saved,
                saved.getContent().stream()
                        .map(entry -> jobService.getJob(entry.getJob().getId()))
                        .toList()
        );
    }

    private boolean sameUser(User first, User second) {
        return first != null
                && second != null
                && first.getId() != null
                && first.getId().equals(second.getId());
    }
}
