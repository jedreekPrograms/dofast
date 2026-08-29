package com.doFast.dofastapp.job.expense;

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

@RestController
@RequestMapping("/jobs/{jobId}/expenses")
public class JobExpenseController {

    private final JobExpenseService expenseService;

    public JobExpenseController(JobExpenseService expenseService) {
        this.expenseService = expenseService;
    }

    @GetMapping
    public JobExpenseSummaryResponse getSummary(
            @PathVariable Long jobId,
            @AuthenticationPrincipal User user
    ) {
        return expenseService.getSummary(jobId, user);
    }

    @PostMapping("/claims")
    @ResponseStatus(HttpStatus.CREATED)
    public JobExpenseClaimResponse createClaim(
            @PathVariable Long jobId,
            @RequestBody @Valid CreateJobExpenseClaimRequest request,
            @AuthenticationPrincipal User user
    ) {
        return expenseService.createClaim(jobId, request, user);
    }
}
