package com.doFast.dofastapp.job.proposal;

import java.math.BigDecimal;

public record JobProposalAcceptanceFundingResponse(
        Long jobId,
        Long proposalId,
        BigDecimal currentEscrowAmount,
        BigDecimal targetEscrowAmount,
        BigDecimal walletContributionAvailable,
        BigDecimal paymentShortfall,
        BigDecimal stripeChargeAmount,
        String currency,
        boolean paymentRequired,
        boolean onlinePaymentAvailable
) {}
