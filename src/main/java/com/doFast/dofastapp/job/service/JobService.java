package com.doFast.dofastapp.job.service;

import com.doFast.dofastapp.common.enums.JobStatus;
import com.doFast.dofastapp.job.dto.JobRequest;
import com.doFast.dofastapp.job.dto.JobResponse;
import com.doFast.dofastapp.job.entity.Job;
import com.doFast.dofastapp.job.repository.JobRepository;
import com.doFast.dofastapp.user.entity.User;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

@Service
public class JobService {
    private final JobRepository jobRepository;

    public JobService(JobRepository jobRepository) {
        this.jobRepository = jobRepository;
    }

    public JobResponse createJob(JobRequest request, User user) {

        Job job = new Job();

        job.setTitle(request.getTitle());
        job.setDescription(request.getDescription());
        job.setPrice(request.getPrice());
        job.setStatus(JobStatus.OPEN);
        job.setCreatedBy(user);

        Job saved = jobRepository.save(job);

        return new JobResponse(
                saved.getId(),
                saved.getTitle(),
                saved.getDescription(),
                saved.getPrice(),
                saved.getStatus().name()
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
                        job.getStatus().name()
                ))
                .collect(Collectors.toList());
    }
}
