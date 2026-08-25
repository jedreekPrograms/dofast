package com.doFast.dofastapp.verification.dto;

import com.doFast.dofastapp.verification.enums.VerificationDecision;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record AdminVerificationDecisionRequest(
        @NotNull VerificationDecision decision,
        @Size(max = 500) String reason
) {}
