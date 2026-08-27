package com.doFast.dofastapp.job.report;

import com.doFast.dofastapp.common.dto.PageResponse;
import com.doFast.dofastapp.user.entity.User;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/admin/job-reports")
@Validated
public class AdminJobReportController {

    private final AdminJobReportService service;

    public AdminJobReportController(AdminJobReportService service) {
        this.service = service;
    }

    @GetMapping
    public PageResponse<AdminJobReportResponse> list(
            @RequestParam(required = false) JobReportStatus status,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size
    ) {
        return service.list(status, page, size);
    }

    @PatchMapping("/{id}")
    public AdminJobReportResponse moderate(
            @PathVariable Long id,
            @RequestBody @Valid ModerateJobReportRequest request,
            @AuthenticationPrincipal User admin
    ) {
        return service.moderate(id, request, admin);
    }

    @PostMapping("/{id}/enforcement")
    public JobReportEnforcementResponse enforce(
            @PathVariable Long id,
            @RequestBody @Valid EnforceJobReportRequest request,
            @AuthenticationPrincipal User admin
    ) {
        return service.enforce(id, request, admin);
    }
}
