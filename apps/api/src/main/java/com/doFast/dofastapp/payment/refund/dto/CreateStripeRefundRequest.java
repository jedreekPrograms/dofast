package com.doFast.dofastapp.payment.refund.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

public record CreateStripeRefundRequest(
        @NotBlank @Size(max = 96) String requestId,
        @NotBlank @Size(max = 255) String paymentIntentId,
        @DecimalMin("0.01") @Digits(integer = 17, fraction = 2) BigDecimal amount
) {
}
