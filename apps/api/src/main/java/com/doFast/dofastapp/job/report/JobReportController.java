package com.doFast.dofastapp.job.report;

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
@RequestMapping("/job-reports")
public class JobReportController {

    private final JobReportService service;

    public JobReportController(JobReportService service) {
        this.service = service;
    }

    @PostMapping("/jobs/{jobId}")
    @ResponseStatus(HttpStatus.CREATED)
    public JobReportResponse report(
            @PathVariable Long jobId,
            @Valid @RequestBody JobReportRequest request,
            @AuthenticationPrincipal User user
    ) {
        return service.report(jobId, request, user);
    }

    @PostMapping("/{reportId}/withdraw")
    public JobReportResponse withdraw(
            @PathVariable Long reportId,
            @AuthenticationPrincipal User user
    ) {
        return service.withdraw(reportId, user);
    }

    @GetMapping("/mine")
    public List<JobReportResponse> mine(@AuthenticationPrincipal User user) {
        return service.mine(user);
    }
}
