package com.doFast.dofastapp.payout.controller;

import com.doFast.dofastapp.payout.dto.CreatePayoutRequest;
import com.doFast.dofastapp.payout.dto.PayoutEligibilityResponse;
import com.doFast.dofastapp.payout.dto.PayoutOnboardingLinkResponse;
import com.doFast.dofastapp.payout.dto.PayoutOnboardingStatusResponse;
import com.doFast.dofastapp.payout.dto.PayoutResponse;
import com.doFast.dofastapp.payout.service.PayoutService;
import com.doFast.dofastapp.payout.service.StripeConnectOnboardingService;
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
@RequestMapping("/wallet/payouts")
public class PayoutController {

    private final PayoutService payoutService;
    private final StripeConnectOnboardingService onboardingService;

    public PayoutController(PayoutService payoutService, StripeConnectOnboardingService onboardingService) {
        this.payoutService = payoutService;
        this.onboardingService = onboardingService;
    }

    @GetMapping("/eligibility")
    public PayoutEligibilityResponse eligibility(@AuthenticationPrincipal User user) {
        return payoutService.eligibility(user);
    }

    @GetMapping("/onboarding/status")
    public PayoutOnboardingStatusResponse onboardingStatus(@AuthenticationPrincipal User user) {
        return onboardingService.cachedStatus(user);
    }

    @PostMapping("/onboarding/refresh")
    public PayoutOnboardingStatusResponse refreshOnboarding(@AuthenticationPrincipal User user) {
        return onboardingService.refreshStatus(user);
    }

    @PostMapping("/onboarding/link")
    public PayoutOnboardingLinkResponse onboardingLink(@AuthenticationPrincipal User user) {
        return onboardingService.createOnboardingLink(user);
    }

    @GetMapping
    public List<PayoutResponse> history(@AuthenticationPrincipal User user) {
        return payoutService.myPayouts(user);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public PayoutResponse request(
            @RequestBody @Valid CreatePayoutRequest request,
            @AuthenticationPrincipal User user
    ) {
        return payoutService.request(request, user);
    }

    @PostMapping("/{payoutId}/cancel")
    public PayoutResponse cancel(@PathVariable Long payoutId, @AuthenticationPrincipal User user) {
        return payoutService.cancel(payoutId, user);
    }
}
