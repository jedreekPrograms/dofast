package com.doFast.dofastapp.dispute.controller;

import com.doFast.dofastapp.chat.dto.ChatHistoryResponse;
import com.doFast.dofastapp.common.dto.PageResponse;
import com.doFast.dofastapp.dispute.dto.DisputeDetailResponse;
import com.doFast.dofastapp.dispute.dto.DisputeResponse;
import com.doFast.dofastapp.dispute.dto.ResolveDisputeRequest;
import com.doFast.dofastapp.dispute.enums.DisputeStatus;
import com.doFast.dofastapp.dispute.service.AdminDisputeEvidenceService;
import com.doFast.dofastapp.dispute.service.DisputeService;
import com.doFast.dofastapp.user.entity.User;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/admin/disputes")
@Validated
public class AdminDisputeController {

    private final DisputeService disputeService;
    private final AdminDisputeEvidenceService evidenceService;

    public AdminDisputeController(
            DisputeService disputeService,
            AdminDisputeEvidenceService evidenceService
    ) {
        this.disputeService = disputeService;
        this.evidenceService = evidenceService;
    }

    @GetMapping
    public PageResponse<DisputeResponse> getDisputes(
            @RequestParam(required = false) DisputeStatus status,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size
    ) {
        return disputeService.getAdminDisputes(status, page, size);
    }

    @GetMapping("/{id}")
    public DisputeDetailResponse getDispute(
            @PathVariable Long id,
            @AuthenticationPrincipal User admin
    ) {
        return disputeService.getAdminDispute(id, admin);
    }

    @GetMapping("/{id}/messages")
    public ChatHistoryResponse getChatEvidence(
            @PathVariable Long id,
            @RequestParam(required = false) Long beforeId,
            @RequestParam(defaultValue = "100") @Min(1) @Max(100) int limit,
            @AuthenticationPrincipal User admin
    ) {
        return evidenceService.getChatEvidence(id, beforeId, limit, admin);
    }

    @PostMapping("/{id}/claim")
    public DisputeDetailResponse claimDispute(
            @PathVariable Long id,
            @AuthenticationPrincipal User admin
    ) {
        return disputeService.claimDispute(id, admin);
    }

    @PostMapping("/{id}/resolve")
    public DisputeDetailResponse resolveDispute(
            @PathVariable Long id,
            @RequestBody @Valid ResolveDisputeRequest request,
            @AuthenticationPrincipal User admin
    ) {
        return disputeService.resolveDispute(id, request, admin);
    }
}
