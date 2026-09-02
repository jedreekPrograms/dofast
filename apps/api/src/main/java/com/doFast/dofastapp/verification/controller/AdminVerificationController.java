package com.doFast.dofastapp.verification.controller;

import com.doFast.dofastapp.common.dto.PageResponse;
import com.doFast.dofastapp.user.entity.User;
import com.doFast.dofastapp.verification.dto.AdminVerificationDecisionRequest;
import com.doFast.dofastapp.verification.dto.AdminVerificationEventResponse;
import com.doFast.dofastapp.verification.dto.AdminVerificationResponse;
import com.doFast.dofastapp.verification.enums.VerificationStatus;
import com.doFast.dofastapp.verification.service.VerificationService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/admin/verifications")
@Validated
public class AdminVerificationController {

    private final VerificationService verificationService;

    public AdminVerificationController(VerificationService verificationService) {
        this.verificationService = verificationService;
    }

    @GetMapping
    public PageResponse<AdminVerificationResponse> list(
            @RequestParam(required = false) VerificationStatus status,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size,
            @AuthenticationPrincipal User admin
    ) {
        return verificationService.getAdminVerifications(status, page, size, admin);
    }

    @GetMapping("/{verificationId}/events")
    public List<AdminVerificationEventResponse> events(
            @PathVariable Long verificationId,
            @AuthenticationPrincipal User admin
    ) {
        return verificationService.getEvents(verificationId, admin);
    }

    @PatchMapping("/{verificationId}")
    public AdminVerificationResponse decide(
            @PathVariable Long verificationId,
            @RequestBody @Valid AdminVerificationDecisionRequest request,
            @AuthenticationPrincipal User admin
    ) {
        return verificationService.decide(
                verificationId,
                request.decision(),
                request.reason(),
                admin
        );
    }
}
