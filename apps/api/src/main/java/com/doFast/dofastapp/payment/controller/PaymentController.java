package com.doFast.dofastapp.payment.controller;

import com.doFast.dofastapp.payment.dto.CreatePaymentIntentRequest;
import com.doFast.dofastapp.payment.dto.CreatePaymentIntentResponse;
import com.doFast.dofastapp.payment.dto.PlatformFeePolicyResponse;
import com.doFast.dofastapp.payment.dto.PlatformFeeQuoteResponse;
import com.doFast.dofastapp.payment.fee.PlatformFeePolicy;
import com.doFast.dofastapp.payment.fee.PlatformFeeQuote;
import com.doFast.dofastapp.payment.service.StripePaymentService;
import com.doFast.dofastapp.user.entity.User;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;

@RestController
@RequestMapping("/payments")
@Validated
public class PaymentController {

    private final StripePaymentService stripePaymentService;
    private final PlatformFeePolicy platformFeePolicy;

    public PaymentController(
            StripePaymentService stripePaymentService,
            PlatformFeePolicy platformFeePolicy
    ) {
        this.stripePaymentService = stripePaymentService;
        this.platformFeePolicy = platformFeePolicy;
    }

    @PostMapping("/create-intent")
    public CreatePaymentIntentResponse createIntent(
            @RequestBody @Valid CreatePaymentIntentRequest request,
            @AuthenticationPrincipal User user
    ) {
        return stripePaymentService.createPaymentIntent(
                request.amount(),
                user.getId(),
                request.requestId()
        );
    }

    @GetMapping("/platform-fee-policy")
    public PlatformFeePolicyResponse getPlatformFeePolicy() {
        return new PlatformFeePolicyResponse(
                platformFeePolicy.currentBasisPoints(),
                platformFeePolicy.currentPercent()
        );
    }

    @GetMapping("/platform-fee-quote")
    public PlatformFeeQuoteResponse getPlatformFeeQuote(
            @RequestParam
            @DecimalMin("0.01")
            @Digits(integer = 17, fraction = 2)
            BigDecimal amount
    ) {
        PlatformFeeQuote quote = platformFeePolicy.quoteCurrent(amount);
        return new PlatformFeeQuoteResponse(
                quote.grossAmount(),
                quote.platformFeeAmount(),
                quote.workerPayoutAmount(),
                quote.basisPoints(),
                platformFeePolicy.currentPercent()
        );
    }
}
