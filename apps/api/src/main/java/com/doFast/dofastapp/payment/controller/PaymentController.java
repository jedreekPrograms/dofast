package com.doFast.dofastapp.payment.controller;

import com.doFast.dofastapp.payment.dto.CreatePaymentIntentRequest;
import com.doFast.dofastapp.payment.dto.CreatePaymentIntentResponse;
import com.doFast.dofastapp.payment.service.StripePaymentService;
import com.doFast.dofastapp.user.entity.User;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/payments")
public class PaymentController {

    private final StripePaymentService stripePaymentService;

    public PaymentController(StripePaymentService stripePaymentService) {
        this.stripePaymentService = stripePaymentService;
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
}
