package com.doFast.dofastapp.job.controller;

import com.doFast.dofastapp.job.dto.JobRequest;
import com.doFast.dofastapp.job.dto.JobResponse;
import com.doFast.dofastapp.job.dto.NearbyJobResponse;
import com.doFast.dofastapp.job.service.JobService;
import com.doFast.dofastapp.location.dto.LocationResponse;
import com.doFast.dofastapp.user.entity.User;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/jobs")
@Validated
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

    @GetMapping("/nearby")
    public List<NearbyJobResponse> getNearbyJobs(
            @RequestParam @DecimalMin("-90.0") @DecimalMax("90.0") double latitude,
            @RequestParam @DecimalMin("-180.0") @DecimalMax("180.0") double longitude,
            @RequestParam(defaultValue = "5000") @Min(100) @Max(50000) int radiusMeters,
            @RequestParam(defaultValue = "50") @Min(1) @Max(100) int limit
    ) {
        return jobService.getNearbyJobs(latitude, longitude, radiusMeters, limit);
    }

    @GetMapping("/{id}")
    public JobResponse getJob(@PathVariable Long id) {
        return jobService.getJob(id);
    }

    @GetMapping("/{id}/location")
    public LocationResponse getExactLocation(@PathVariable Long id, @AuthenticationPrincipal User user) {
        return jobService.getExactLocation(id, user);
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
