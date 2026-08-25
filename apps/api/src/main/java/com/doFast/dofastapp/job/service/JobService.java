package com.doFast.dofastapp.job.service;

import com.doFast.dofastapp.common.enums.JobStatus;
import com.doFast.dofastapp.common.exception.BusinessException;
import com.doFast.dofastapp.job.dto.JobRequest;
import com.doFast.dofastapp.job.dto.JobResponse;
import com.doFast.dofastapp.job.entity.Job;
import com.doFast.dofastapp.job.repository.JobRepository;
import com.doFast.dofastapp.payment.service.TransactionService;
import com.doFast.dofastapp.user.entity.User;
import jakarta.transaction.Transactional;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@Transactional
public class JobService {

    private final JobRepository jobRepository;
    private final TransactionService transactionService;

    public JobService(JobRepository jobRepository, TransactionService transactionService) {
        this.jobRepository = jobRepository;
        this.transactionService = transactionService;
    }

    public JobResponse createJob(JobRequest request, User user) {
        Job job = new Job();
        job.setTitle(request.getTitle());
        job.setDescription(request.getDescription());
        job.setPrice(request.getPrice());
        job.setStatus(JobStatus.OPEN);
        job.setCreatedBy(user);

        Job saved = jobRepository.save(job);
        transactionService.holdMoney(saved);

        return toResponse(saved);
    }

    public List<JobResponse> getOpenJobs() {
        return jobRepository.findByStatus(JobStatus.OPEN)
                .stream()
                .map(this::toResponse)
                .toList();
    }

    public JobResponse takeJob(Long jobId, User currentUser) {
        Job job = getJob(jobId);

        if (job.getStatus() != JobStatus.OPEN) {
            throw new BusinessException("Zlecenie nie jest dostępne");
        }

        if (job.getCreatedBy().getId().equals(currentUser.getId())) {
            throw new BusinessException("Nie możesz wziąć własnego zlecenia");
        }

        job.setStatus(JobStatus.IN_PROGRESS);
        job.setTakenBy(currentUser);

        return toResponse(jobRepository.save(job));
    }

    public List<JobResponse> getMyJobs(User user) {
        return jobRepository.findByCreatedByOrTakenBy(user, user)
                .stream()
                .map(this::toResponse)
                .toList();
    }

    public JobResponse markAsDone(Long jobId, User currentUser) {
        Job job = getJob(jobId);

        if (job.getStatus() != JobStatus.IN_PROGRESS) {
            throw new BusinessException("Zlecenie nie jest w trakcie");
        }

        if (!job.getCreatedBy().getId().equals(currentUser.getId())) {
            throw new BusinessException("Tylko autor może oznaczyć zlecenie jako wykonane");
        }

        job.setStatus(JobStatus.DONE);
        Job saved = jobRepository.save(job);
        transactionService.releaseMoney(saved, saved.getTakenBy());

        return toResponse(saved);
    }

    public JobResponse cancelJob(Long jobId, User currentUser) {
        Job job = getJob(jobId);

        if (!job.getCreatedBy().getId().equals(currentUser.getId())) {
            throw new BusinessException("Tylko autor może anulować zlecenie");
        }

        if (job.getStatus() == JobStatus.DONE) {
            throw new BusinessException("Nie można anulować zakończonego zlecenia");
        }

        job.setStatus(JobStatus.CANCELLED);
        Job saved = jobRepository.save(job);
        transactionService.refundMoney(saved);

        return toResponse(saved);
    }

    private Job getJob(Long jobId) {
        return jobRepository.findById(jobId)
                .orElseThrow(() -> new BusinessException("Zlecenie nie istnieje"));
    }

    private JobResponse toResponse(Job job) {
        return new JobResponse(
                job.getId(),
                job.getTitle(),
                job.getDescription(),
                job.getPrice(),
                job.getStatus().name(),
                job.getTakenBy() != null ? job.getTakenBy().getId() : null
        );
    }
}
