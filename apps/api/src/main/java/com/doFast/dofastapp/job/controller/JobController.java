package com.doFast.dofastapp.job.controller;

import com.doFast.dofastapp.job.dto.JobRequest;
import com.doFast.dofastapp.job.dto.JobResponse;
import com.doFast.dofastapp.job.service.JobService;
import com.doFast.dofastapp.user.entity.User;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/jobs")
public class JobController {

    private final JobService jobService;

    public JobController(JobService jobService) {
        this.jobService = jobService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public JobResponse createJob(
            @RequestBody @Valid JobRequest request,
            @AuthenticationPrincipal User user
    ) {
        return jobService.createJob(request, user);
    }

    @GetMapping
    public List<JobResponse> getJobs() {
        return jobService.getOpenJobs();
    }

    @GetMapping("/{id}")
    public JobResponse getJob(@PathVariable Long id) {
        return jobService.getJob(id);
    }

    @PostMapping({"/{id}/accept", "/{id}/take"})
    public JobResponse acceptJob(@PathVariable Long id, @AuthenticationPrincipal User user) {
        return jobService.acceptJob(id, user);
    }

    @GetMapping("/my")
    public List<JobResponse> getMyJobs(@AuthenticationPrincipal User user) {
        return jobService.getMyJobs(user);
    }

    @PostMapping("/{id}/completion")
    public JobResponse requestCompletion(@PathVariable Long id, @AuthenticationPrincipal User user) {
        return jobService.requestCompletion(id, user);
    }

    @PostMapping({"/{id}/confirm", "/{id}/done"})
    public JobResponse confirmCompletion(@PathVariable Long id, @AuthenticationPrincipal User user) {
        return jobService.confirmCompletion(id, user);
    }

    @PostMapping("/{id}/cancel")
    public JobResponse cancelJob(@PathVariable Long id, @AuthenticationPrincipal User user) {
        return jobService.cancelJob(id, user);
    }
}
