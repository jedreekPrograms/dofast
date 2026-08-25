package com.doFast.dofastapp.job.service;

import com.doFast.dofastapp.common.enums.JobStatus;
import com.doFast.dofastapp.common.exception.ConflictException;
import com.doFast.dofastapp.common.exception.ForbiddenOperationException;
import com.doFast.dofastapp.common.exception.ResourceNotFoundException;
import com.doFast.dofastapp.job.dto.JobRequest;
import com.doFast.dofastapp.job.dto.JobResponse;
import com.doFast.dofastapp.job.entity.Job;
import com.doFast.dofastapp.job.repository.JobRepository;
import com.doFast.dofastapp.payment.service.TransactionService;
import com.doFast.dofastapp.user.entity.User;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Service
@Transactional(readOnly = true)
public class JobService {

    private final JobRepository jobRepository;
    private final TransactionService transactionService;

    public JobService(JobRepository jobRepository, TransactionService transactionService) {
        this.jobRepository = jobRepository;
        this.transactionService = transactionService;
    }

    @Transactional
    public JobResponse createJob(JobRequest request, User user) {
        Job job = new Job();
        job.setTitle(request.getTitle().trim());
        job.setDescription(request.getDescription().trim());
        job.setPrice(request.getPrice());
        job.setStatus(JobStatus.OPEN);
        job.setCreatedBy(user);

        Job saved = jobRepository.save(job);
        transactionService.holdMoney(saved);

        return toResponse(saved);
    }

    public List<JobResponse> getOpenJobs() {
        return jobRepository.findByStatusOrderByCreatedAtDesc(JobStatus.OPEN)
                .stream()
                .map(this::toResponse)
                .toList();
    }

    public JobResponse getJob(Long jobId) {
        return toResponse(getJobForRead(jobId));
    }

    @Transactional
    public JobResponse acceptJob(Long jobId, User currentUser) {
        Job job = getJobForUpdate(jobId);

        if (job.getStatus() != JobStatus.OPEN) {
            throw new ConflictException("Zlecenie nie jest już dostępne");
        }

        if (sameUser(job.getCreatedBy(), currentUser)) {
            throw new ForbiddenOperationException("Nie możesz przyjąć własnego zlecenia");
        }

        job.assignTo(currentUser, LocalDateTime.now());
        return toResponse(jobRepository.save(job));
    }

    public List<JobResponse> getMyJobs(User user) {
        return jobRepository.findByCreatedByOrTakenByOrderByCreatedAtDesc(user, user)
                .stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional
    public JobResponse requestCompletion(Long jobId, User currentUser) {
        Job job = getJobForUpdate(jobId);

        if (job.getStatus() != JobStatus.IN_PROGRESS) {
            throw new ConflictException("Zlecenie nie jest w trakcie realizacji");
        }

        if (job.getTakenBy() == null || !sameUser(job.getTakenBy(), currentUser)) {
            throw new ForbiddenOperationException("Tylko wykonawca może zgłosić wykonanie zlecenia");
        }

        job.requestCompletion(LocalDateTime.now());
        return toResponse(jobRepository.save(job));
    }

    @Transactional
    public JobResponse confirmCompletion(Long jobId, User currentUser) {
        Job job = getJobForUpdate(jobId);

        if (!sameUser(job.getCreatedBy(), currentUser)) {
            throw new ForbiddenOperationException("Tylko autor może potwierdzić wykonanie zlecenia");
        }

        if (job.getStatus() != JobStatus.COMPLETION_REQUESTED) {
            throw new ConflictException("Wykonawca nie zgłosił jeszcze wykonania zlecenia");
        }

        if (job.getTakenBy() == null) {
            throw new ConflictException("Zlecenie nie ma przypisanego wykonawcy");
        }

        job.complete(LocalDateTime.now());
        Job saved = jobRepository.save(job);
        transactionService.releaseMoney(saved, saved.getTakenBy());

        return toResponse(saved);
    }

    @Transactional
    public JobResponse cancelJob(Long jobId, User currentUser) {
        Job job = getJobForUpdate(jobId);

        if (!sameUser(job.getCreatedBy(), currentUser)) {
            throw new ForbiddenOperationException("Tylko autor może anulować zlecenie");
        }

        if (job.getStatus() != JobStatus.OPEN) {
            throw new ConflictException("Można anulować tylko zlecenie, którego nikt jeszcze nie przyjął");
        }

        job.cancel(LocalDateTime.now());
        Job saved = jobRepository.save(job);
        transactionService.refundMoney(saved);

        return toResponse(saved);
    }

    private Job getJobForRead(Long jobId) {
        return jobRepository.findById(jobId)
                .orElseThrow(() -> new ResourceNotFoundException("Zlecenie nie istnieje"));
    }

    private Job getJobForUpdate(Long jobId) {
        return jobRepository.findByIdForUpdate(jobId)
                .orElseThrow(() -> new ResourceNotFoundException("Zlecenie nie istnieje"));
    }

    private boolean sameUser(User first, User second) {
        return first != null
                && second != null
                && first.getId() != null
                && first.getId().equals(second.getId());
    }

    private JobResponse toResponse(Job job) {
        return new JobResponse(
                job.getId(),
                job.getTitle(),
                job.getDescription(),
                job.getPrice(),
                job.getStatus(),
                job.getCreatedBy().getId(),
                job.getTakenBy() != null ? job.getTakenBy().getId() : null,
                job.getCreatedAt(),
                job.getUpdatedAt(),
                job.getTakenAt(),
                job.getCompletionRequestedAt(),
                job.getCompletedAt(),
                job.getCancelledAt()
        );
    }
}
