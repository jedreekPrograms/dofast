package com.doFast.dofastapp.payout.controller;

import com.doFast.dofastapp.common.dto.PageResponse;
import com.doFast.dofastapp.payout.dto.AdminPayoutEventResponse;
import com.doFast.dofastapp.payout.dto.AdminPayoutFailureRequest;
import com.doFast.dofastapp.payout.dto.AdminPayoutResponse;
import com.doFast.dofastapp.payout.enums.PayoutStatus;
import com.doFast.dofastapp.payout.service.AdminPayoutService;
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

import java.util.List;

@RestController
@RequestMapping("/admin/payouts")
@Validated
public class AdminPayoutController {

    private final AdminPayoutService payoutService;

    public AdminPayoutController(AdminPayoutService payoutService) {
        this.payoutService = payoutService;
    }

    @GetMapping
    public PageResponse<AdminPayoutResponse> list(
            @RequestParam(required = false) PayoutStatus status,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size,
            @AuthenticationPrincipal User admin
    ) {
        return payoutService.list(status, page, size, admin);
    }

    @GetMapping("/{payoutId}/events")
    public List<AdminPayoutEventResponse> events(
            @PathVariable Long payoutId,
            @AuthenticationPrincipal User admin
    ) {
        return payoutService.events(payoutId, admin);
    }

    @PostMapping("/{payoutId}/retry")
    public AdminPayoutResponse retry(@PathVariable Long payoutId, @AuthenticationPrincipal User admin) {
        return payoutService.retry(payoutId, admin);
    }

    @PostMapping("/{payoutId}/fail")
    public AdminPayoutResponse fail(
            @PathVariable Long payoutId,
            @RequestBody @Valid AdminPayoutFailureRequest request,
            @AuthenticationPrincipal User admin
    ) {
        return payoutService.failAndRestore(payoutId, request.reason(), admin);
    }
}
