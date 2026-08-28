package com.doFast.dofastapp.job.proposal;

import com.doFast.dofastapp.user.entity.User;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/jobs/{jobId}/proposals")
public class JobProposalController {

    private final JobProposalService jobProposalService;

    public JobProposalController(JobProposalService jobProposalService) {
        this.jobProposalService = jobProposalService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public JobProposalResponse submit(
            @PathVariable Long jobId,
            @RequestBody @Valid CreateJobProposalRequest request,
            @AuthenticationPrincipal User user
    ) {
        return jobProposalService.submit(jobId, request, user);
    }

    @GetMapping
    public List<JobProposalResponse> listVisible(
            @PathVariable Long jobId,
            @AuthenticationPrincipal User user
    ) {
        return jobProposalService.listVisible(jobId, user);
    }

    @DeleteMapping("/{proposalId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void withdraw(
            @PathVariable Long jobId,
            @PathVariable Long proposalId,
            @AuthenticationPrincipal User user
    ) {
        jobProposalService.withdraw(jobId, proposalId, user);
    }

    @PostMapping("/{proposalId}/accept")
    public AcceptedJobProposalResponse accept(
            @PathVariable Long jobId,
            @PathVariable Long proposalId,
            @AuthenticationPrincipal User user
    ) {
        return jobProposalService.accept(jobId, proposalId, user);
    }
}
