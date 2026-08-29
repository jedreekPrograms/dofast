package com.doFast.dofastapp.dispute.dto;

import com.doFast.dofastapp.dispute.enums.DisputeResolution;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

public record ResolveDisputeRequest(
        @NotNull DisputeResolution resolution,
        @NotBlank @Size(max = 4000) String note,
        @DecimalMin(value = "0.00") @Digits(integer = 5, fraction = 2) BigDecimal approvedExpenseAmount
) {}
