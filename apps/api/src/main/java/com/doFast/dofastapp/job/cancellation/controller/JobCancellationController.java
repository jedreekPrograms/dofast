package com.doFast.dofastapp.job.cancellation.controller;

import com.doFast.dofastapp.job.cancellation.dto.CreateJobCancellationRequest;
import com.doFast.dofastapp.job.cancellation.dto.JobCancellationResponse;
import com.doFast.dofastapp.job.cancellation.service.JobCancellationService;
import com.doFast.dofastapp.user.entity.User;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/jobs/{jobId}/cancellation")
public class JobCancellationController {

    private final JobCancellationService cancellationService;

    public JobCancellationController(JobCancellationService cancellationService) {
        this.cancellationService = cancellationService;
    }

    @GetMapping
    public ResponseEntity<JobCancellationResponse> getPending(
            @PathVariable Long jobId,
            @AuthenticationPrincipal User user
    ) {
        return cancellationService.getPending(jobId, user)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.noContent().build());
    }

    @PostMapping
    public JobCancellationResponse requestCancellation(
            @PathVariable Long jobId,
            @RequestBody @Valid CreateJobCancellationRequest request,
            @AuthenticationPrincipal User user
    ) {
        return cancellationService.requestCancellation(jobId, request, user);
    }

    @PostMapping("/approve")
    public JobCancellationResponse approve(
            @PathVariable Long jobId,
            @AuthenticationPrincipal User user
    ) {
        return cancellationService.approve(jobId, user);
    }

    @PostMapping("/decline")
    public JobCancellationResponse decline(
            @PathVariable Long jobId,
            @AuthenticationPrincipal User user
    ) {
        return cancellationService.decline(jobId, user);
    }

    @PostMapping("/withdraw")
    public JobCancellationResponse withdraw(
            @PathVariable Long jobId,
            @AuthenticationPrincipal User user
    ) {
        return cancellationService.withdraw(jobId, user);
    }
}
