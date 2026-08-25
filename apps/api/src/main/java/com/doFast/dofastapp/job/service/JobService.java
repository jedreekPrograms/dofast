package com.doFast.dofastapp.job.service;

import com.doFast.dofastapp.common.enums.JobStatus;
import com.doFast.dofastapp.common.exception.BusinessException;
import com.doFast.dofastapp.job.dto.JobRequest;
import com.doFast.dofastapp.job.dto.JobResponse;
import com.doFast.dofastapp.job.entity.Job;
import com.doFast.dofastapp.job.repository.JobRepository;
import com.doFast.dofastapp.payment.service.TransactionService;
import com.doFast.dofastapp.user.entity.User;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

@Service
public class JobService {
    private final JobRepository jobRepository;
    private final TransactionService transcationService;

    public JobService(JobRepository jobRepository, TransactionService transcationService) {
        this.jobRepository = jobRepository;
        this.transcationService = transcationService;
    }

    public JobResponse createJob(JobRequest request, User user) {

        Job job = new Job();

        job.setTitle(request.getTitle());
        job.setDescription(request.getDescription());
        job.setPrice(request.getPrice());
        job.setStatus(JobStatus.OPEN);
        job.setCreatedBy(user);

        Job saved = jobRepository.save(job);
        transcationService.holdMoney(saved);

        return new JobResponse(
                saved.getId(),
                saved.getTitle(),
                saved.getDescription(),
                saved.getPrice(),
                saved.getStatus().name(),
                saved.getTakenBy() != null ? job.getTakenBy().getId() : null
        );

    }

    public List<JobResponse> getOpenJobs() {
        return jobRepository.findByStatus(JobStatus.OPEN)
                .stream()
                .map(job -> new JobResponse(
                        job.getId(),
                        job.getTitle(),
                        job.getDescription(),
                        job.getPrice(),
                        job.getStatus().name(),
                        job.getTakenBy() != null ? job.getTakenBy().getId() : null
                ))
                .collect(Collectors.toList());
    }

    public JobResponse takeJob(Long jobId, User currentUser) {

        Job job = jobRepository.findById(jobId)
                .orElseThrow(() -> new BusinessException("Zlecenie nie istnieje"));

        if (job.getStatus() != JobStatus.OPEN) {
            throw new BusinessException("Zlecenie nie jest dostępne");
        }

        if (job.getCreatedBy().getId().equals(currentUser.getId())) {
            throw new BusinessException("Nie możesz wziąć własnego zlecenia");
        }

        job.setStatus(JobStatus.IN_PROGRESS);
        job.setTakenBy(currentUser);

        Job saved = jobRepository.save(job);

        return new JobResponse(
                saved.getId(),
                saved.getTitle(),
                saved.getDescription(),
                saved.getPrice(),
                saved.getStatus().name(),
                saved.getTakenBy().getId()
        );
    }

    public List<JobResponse> getMyJobs(User user) {
        return jobRepository
                .findByCreatedByOrTakenBy(user, user)
                .stream()
                .map(job -> new JobResponse(
                        job.getId(),
                        job.getTitle(),
                        job.getDescription(),
                        job.getPrice(),
                        job.getStatus().name(),
                        job.getTakenBy() != null ? job.getTakenBy().getId() : null
                ))
                .collect(Collectors.toList());
    }

    public JobResponse markAsDone(Long jobId, User currentUser) {

        Job job = jobRepository.findById(jobId)
                .orElseThrow(() -> new BusinessException("Zlecenie nie istnieje"));

        if (job.getStatus() != JobStatus.IN_PROGRESS) {
            throw new BusinessException("Zlecenie nie jest w trakcie");
        }

        if (!job.getCreatedBy().getId().equals(currentUser.getId())) {
            throw new BusinessException("Tylko autor może oznaczyć zlecenie jako wykonane");
        }

        job.setStatus(JobStatus.DONE);

        Job saved = jobRepository.save(job);
        transcationService.releaseMoney(saved, saved.getTakenBy());

        return new JobResponse(
                saved.getId(),
                saved.getTitle(),
                saved.getDescription(),
                saved.getPrice(),
                saved.getStatus().name(),
                saved.getTakenBy() != null ? saved.getTakenBy().getId() : null
        );
    }

    public JobResponse cancelJob(Long jobId, User currentUser) {

        Job job = jobRepository.findById(jobId)
                .orElseThrow(() -> new BusinessException("Zlecenie nie istnieje"));

        if (!job.getCreatedBy().getId().equals(currentUser.getId())) {
            throw new BusinessException("Tylko autor może anulować zlecenie");
        }

        if (job.getStatus() == JobStatus.DONE) {
            throw new BusinessException("Nie można anulować zakończonego zlecenia");
        }

        job.setStatus(JobStatus.CANCELLED);
        Job saved = jobRepository.save(job);

        transcationService.refundMoney(saved);

        return new JobResponse(
                saved.getId(),
                saved.getTitle(),
                saved.getDescription(),
                saved.getPrice(),
                saved.getStatus().name(),
                saved.getTakenBy() != null ? saved.getTakenBy().getId() : null
        );
    }
}
