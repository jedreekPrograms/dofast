package com.doFast.dofastapp.job.controller;

import com.doFast.dofastapp.job.dto.JobRequest;
import com.doFast.dofastapp.job.dto.JobResponse;
import com.doFast.dofastapp.job.service.JobService;
import com.doFast.dofastapp.user.entity.User;
import jakarta.validation.Valid;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/jobs")
public class JobController {

    private final JobService jobService;

    public JobController(JobService jobService) {
        this.jobService = jobService;
    }

    @PostMapping
    public JobResponse createJob(@RequestBody @Valid JobRequest request) {

        Authentication authentication =
                SecurityContextHolder.getContext().getAuthentication();

        User user = (User) authentication.getPrincipal();

        return jobService.createJob(request, user);
    }

    @GetMapping
    public List<JobResponse> getJobs() {
        return jobService.getOpenJobs();
    }

    @PostMapping("/{id}/take")
    public JobResponse takeJob(@PathVariable Long id) {

        Authentication authentication =
                SecurityContextHolder.getContext().getAuthentication();

        User user = (User) authentication.getPrincipal();

        return jobService.takeJob(id, user);
    }

    @GetMapping("/my")
    public List<JobResponse> getMyJobs() {
        Authentication authentication =
                SecurityContextHolder.getContext().getAuthentication();

        User user = (User) authentication.getPrincipal();

        return jobService.getMyJobs(user);
    }

    @PostMapping("/{id}/done")
    public JobResponse markAsDone(@PathVariable Long id) {

        Authentication authentication =
                SecurityContextHolder.getContext().getAuthentication();

        User user = (User) authentication.getPrincipal();

        return jobService.markAsDone(id, user);
    }
}
