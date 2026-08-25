package com.doFast.dofastapp.location.tracking.service;

import com.doFast.dofastapp.common.enums.JobStatus;
import com.doFast.dofastapp.common.exception.ConflictException;
import com.doFast.dofastapp.common.exception.ForbiddenOperationException;
import com.doFast.dofastapp.common.exception.ResourceNotFoundException;
import com.doFast.dofastapp.job.entity.Job;
import com.doFast.dofastapp.job.repository.JobRepository;
import com.doFast.dofastapp.user.entity.User;
import org.springframework.stereotype.Service;

@Service
public class LiveTrackingAccessService {

    private final JobRepository jobRepository;

    public LiveTrackingAccessService(JobRepository jobRepository) {
        this.jobRepository = jobRepository;
    }

    public Job requireViewer(Long jobId, User user) {
        Job job = getJob(jobId);
        requireActiveTrackingStatus(job);
        if (!sameUser(job.getCreatedBy(), user) && !sameUser(job.getTakenBy(), user)) {
            throw new ForbiddenOperationException("Lokalizacja wykonawcy jest dostępna tylko dla stron aktywnego zlecenia");
        }
        return job;
    }

    public Job requireWorker(Long jobId, User user) {
        Job job = getJob(jobId);
        requireActiveTrackingStatus(job);
        if (!sameUser(job.getTakenBy(), user)) {
            throw new ForbiddenOperationException("Tylko przypisany wykonawca może udostępniać lokalizację");
        }
        return job;
    }

    public static boolean isTrackingActive(Job job) {
        return job.getStatus() == JobStatus.IN_PROGRESS
                || job.getStatus() == JobStatus.COMPLETION_REQUESTED;
    }

    private Job getJob(Long jobId) {
        return jobRepository.findById(jobId)
                .orElseThrow(() -> new ResourceNotFoundException("Zlecenie nie istnieje"));
    }

    private void requireActiveTrackingStatus(Job job) {
        if (!isTrackingActive(job)) {
            throw new ConflictException("Śledzenie lokalizacji nie jest aktywne dla tego zlecenia");
        }
    }

    private boolean sameUser(User first, User second) {
        return first != null && second != null && first.getId() != null && first.getId().equals(second.getId());
    }
}
