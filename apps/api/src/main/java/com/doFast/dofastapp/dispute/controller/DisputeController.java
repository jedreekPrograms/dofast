package com.doFast.dofastapp.dispute.controller;

import com.doFast.dofastapp.dispute.dto.CreateDisputeRequest;
import com.doFast.dofastapp.dispute.dto.DisputeDetailResponse;
import com.doFast.dofastapp.dispute.dto.DisputeResponse;
import com.doFast.dofastapp.dispute.service.DisputeService;
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
@RequestMapping("/disputes")
public class DisputeController {

    private final DisputeService disputeService;

    public DisputeController(DisputeService disputeService) {
        this.disputeService = disputeService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public DisputeDetailResponse openDispute(
            @RequestBody @Valid CreateDisputeRequest request,
            @AuthenticationPrincipal User user
    ) {
        return disputeService.openDispute(request, user);
    }

    @GetMapping("/my")
    public List<DisputeResponse> getMyDisputes(@AuthenticationPrincipal User user) {
        return disputeService.getMyDisputes(user);
    }

    @GetMapping("/{id}")
    public DisputeDetailResponse getDispute(
            @PathVariable Long id,
            @AuthenticationPrincipal User user
    ) {
        return disputeService.getDispute(id, user);
    }

    @PostMapping("/{id}/cancel")
    public DisputeDetailResponse cancelDispute(
            @PathVariable Long id,
            @AuthenticationPrincipal User user
    ) {
        return disputeService.cancelDispute(id, user);
    }
}
