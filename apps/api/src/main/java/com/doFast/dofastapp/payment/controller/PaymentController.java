package com.doFast.dofastapp.payment.controller;

import com.doFast.dofastapp.common.exception.ForbiddenOperationException;
import com.doFast.dofastapp.payment.dto.CreatePaymentIntentRequest;
import com.doFast.dofastapp.payment.dto.CreatePaymentIntentResponse;
import com.doFast.dofastapp.payment.dto.PlatformFeePolicyResponse;
import com.doFast.dofastapp.payment.dto.PlatformFeeQuoteResponse;
import com.doFast.dofastapp.payment.fee.PlatformFeePolicy;
import com.doFast.dofastapp.payment.fee.PlatformFeeQuote;
import com.doFast.dofastapp.payment.fee.PlatformFeeQuoteService;
import com.doFast.dofastapp.payment.refund.dto.CreateStripeRefundRequest;
import com.doFast.dofastapp.payment.refund.dto.StripeRefundResponse;
import com.doFast.dofastapp.payment.refund.service.StripeRefundCoordinator;
import com.doFast.dofastapp.payment.service.StripePaymentService;
import com.doFast.dofastapp.user.entity.User;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
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

import java.math.BigDecimal;

@RestController
@RequestMapping("/payments")
@Validated
public class PaymentController {

    private final StripePaymentService stripePaymentService;
    private final PlatformFeePolicy platformFeePolicy;
    private final PlatformFeeQuoteService platformFeeQuoteService;
    private final StripeRefundCoordinator refundCoordinator;

    public PaymentController(
            StripePaymentService stripePaymentService,
            PlatformFeePolicy platformFeePolicy,
            PlatformFeeQuoteService platformFeeQuoteService,
            StripeRefundCoordinator refundCoordinator
    ) {
        this.stripePaymentService = stripePaymentService;
        this.platformFeePolicy = platformFeePolicy;
        this.platformFeeQuoteService = platformFeeQuoteService;
        this.refundCoordinator = refundCoordinator;
    }

    @PostMapping("/create-intent")
    public CreatePaymentIntentResponse createIntent(
            @RequestBody @Valid CreatePaymentIntentRequest request,
            @AuthenticationPrincipal User user
    ) {
        return stripePaymentService.createPaymentIntent(
                request.amount(),
                requireActorId(user),
                request.requestId()
        );
    }

    @PostMapping("/refunds")
    public StripeRefundResponse requestRefund(
            @RequestBody @Valid CreateStripeRefundRequest request,
            @AuthenticationPrincipal User user
    ) {
        return refundCoordinator.request(requireActorId(user), request);
    }

    @GetMapping("/refunds/{refundRequestId}")
    public StripeRefundResponse getRefund(
            @PathVariable @Min(1) Long refundRequestId,
            @AuthenticationPrincipal User user
    ) {
        return refundCoordinator.get(refundRequestId, requireActorId(user));
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
            BigDecimal amount,
            @RequestParam(required = false) @Min(1) Long jobId,
            @AuthenticationPrincipal User user
    ) {
        PlatformFeeQuote quote = jobId == null
                ? platformFeeQuoteService.quoteCurrent(amount)
                : platformFeeQuoteService.quoteForJob(jobId, amount, user);
        return new PlatformFeeQuoteResponse(
                quote.grossAmount(),
                quote.platformFeeAmount(),
                quote.workerPayoutAmount(),
                quote.basisPoints(),
                BigDecimal.valueOf(quote.basisPoints()).movePointLeft(2)
        );
    }

    private Long requireActorId(User user) {
        if (user == null || user.getId() == null) {
            throw new ForbiddenOperationException("Zaloguj się, aby zarządzać płatnościami");
        }
        return user.getId();
    }
}
