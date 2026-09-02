package com.doFast.dofastapp.job.saved;

import com.doFast.dofastapp.common.dto.PageResponse;
import com.doFast.dofastapp.common.enums.JobStatus;
import com.doFast.dofastapp.common.exception.ForbiddenOperationException;
import com.doFast.dofastapp.common.exception.ResourceNotFoundException;
import com.doFast.dofastapp.job.dto.JobResponse;
import com.doFast.dofastapp.job.entity.Job;
import com.doFast.dofastapp.job.repository.JobRepository;
import com.doFast.dofastapp.job.service.JobService;
import com.doFast.dofastapp.user.entity.User;
import com.doFast.dofastapp.user.service.UserBlockService;
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
    private final UserBlockService userBlockService;

    public SavedJobService(
            SavedJobRepository savedJobRepository,
            JobRepository jobRepository,
            JobService jobService,
            UserBlockService userBlockService
    ) {
        this.savedJobRepository = savedJobRepository;
        this.jobRepository = jobRepository;
        this.jobService = jobService;
        this.userBlockService = userBlockService;
    }

    @Transactional
    public void save(Long jobId, User user) {
        Long userId = requireUserId(user);
        Job job = jobRepository.findByIdAndStatus(jobId, JobStatus.OPEN)
                .orElseThrow(() -> new ResourceNotFoundException("Zlecenie nie istnieje"));
        if (sameUser(job.getCreatedBy(), userId)) {
            throw new ForbiddenOperationException("Nie możesz zapisać własnego zlecenia");
        }
        if (userBlockService.isInteractionBlocked(job.getCreatedBy(), user)) {
            throw new ForbiddenOperationException("Nie możesz zapisać tego zlecenia");
        }
        if (!savedJobRepository.existsByUser_IdAndJob_Id(userId, jobId)) {
            savedJobRepository.save(new SavedJob(user, job));
        }
    }

    @Transactional
    public void remove(Long jobId, User user) {
        Long userId = requireUserId(user);
        savedJobRepository.deleteByUser_IdAndJob_Id(userId, jobId);
    }

    public SavedJobStatusResponse status(Long jobId, User user) {
        Long userId = requireUserId(user);
        return new SavedJobStatusResponse(savedJobRepository.existsByUser_IdAndJob_Id(userId, jobId));
    }

    public SavedJobBatchStatusResponse statuses(List<Long> jobIds, User user) {
        Long userId = requireUserId(user);
        List<Long> uniqueIds = jobIds.stream().distinct().toList();
        List<Long> savedIds = savedJobRepository.findSavedJobIds(userId, uniqueIds);
        return new SavedJobBatchStatusResponse(new LinkedHashSet<>(savedIds));
    }

    @Transactional
    public PageResponse<JobResponse> list(User user, int page, int size) {
        Long userId = requireUserId(user);
        savedJobRepository.deleteByUserAndJobStatusNot(userId, JobStatus.OPEN);
        Page<SavedJob> saved = savedJobRepository.findByUserAndJobStatus(
                userId,
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

    private Long requireUserId(User user) {
        if (user == null || user.getId() == null) {
            throw new ForbiddenOperationException("Zaloguj się, aby zarządzać zapisanymi zleceniami");
        }
        return user.getId();
    }

    private boolean sameUser(User first, Long secondUserId) {
        return first != null
                && first.getId() != null
                && first.getId().equals(secondUserId);
    }
}
