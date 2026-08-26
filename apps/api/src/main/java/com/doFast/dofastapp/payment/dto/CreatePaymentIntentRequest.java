package com.doFast.dofastapp.payment.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

public record CreatePaymentIntentRequest(
        @NotNull
        @DecimalMin(value = "1.00")
        @DecimalMax(value = "10000.00")
        @Digits(integer = 5, fraction = 2)
        BigDecimal amount,

        @NotBlank
        @Size(max = 64)
        @Pattern(regexp = "[A-Za-z0-9_-]+")
        String requestId
) {}
