package com.doFast.dofastapp.payout.service;

import com.doFast.dofastapp.payout.config.PayoutProperties;
import com.doFast.dofastapp.payout.entity.PayoutRequest;
import com.doFast.dofastapp.payout.enums.PayoutStatus;
import com.doFast.dofastapp.payout.provider.PayoutSubmittedReconciliationCommand;
import com.doFast.dofastapp.payout.repository.PayoutRequestRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Locale;
import java.util.Optional;

@Service
public class PayoutSubmittedReconciliationQueue {

    private final PayoutRequestRepository payoutRepository;
    private final PayoutProperties properties;

    public PayoutSubmittedReconciliationQueue(
            PayoutRequestRepository payoutRepository,
            PayoutProperties properties
    ) {
        this.payoutRepository = payoutRepository;
        this.properties = properties;
    }

    @Transactional
    public Optional<PayoutSubmittedReconciliationCommand> claimNext(String providerCode) {
        LocalDateTime now = LocalDateTime.now();
        Optional<PayoutRequest> candidate = payoutRepository.findNextSubmittedForReconciliationForUpdate(
                providerCode,
                now
        );
        if (candidate.isEmpty()) return Optional.empty();

        PayoutRequest payout = candidate.get();
        payout.scheduleSubmittedReconciliation(now.plus(properties.submittedReconciliationDelay()));
        payoutRepository.saveAndFlush(payout);

        return Optional.of(new PayoutSubmittedReconciliationCommand(
                payout.getId(),
                payout.getUser().getId(),
                payout.getAmount(),
                payout.getCurrency(),
                payout.getProviderCode(),
                payout.getProviderReference(),
                payout.getProviderTransferReference()
        ));
    }

    @Transactional
    public void recordProviderFailure(Long payoutId, String failureCode) {
        PayoutRequest payout = payoutRepository.findByIdForUpdate(payoutId).orElse(null);
        if (payout == null || payout.getStatus() != PayoutStatus.SUBMITTED) return;
        payout.recordSubmittedReconciliationFailure(normalizeFailureCode(failureCode), LocalDateTime.now());
        payoutRepository.save(payout);
    }

    @Transactional
    public void recordProviderHealthy(Long payoutId) {
        PayoutRequest payout = payoutRepository.findByIdForUpdate(payoutId).orElse(null);
        if (payout == null || payout.getStatus() != PayoutStatus.SUBMITTED) return;
        payout.clearSubmittedReconciliationFailure();
        payoutRepository.save(payout);
    }

    private String normalizeFailureCode(String value) {
        if (value == null || value.isBlank()) return "RECONCILIATION_ERROR";
        String normalized = value.trim().toUpperCase(Locale.ROOT).replaceAll("[^A-Z0-9_-]", "_");
        return normalized.length() <= 64 ? normalized : normalized.substring(0, 64);
    }
}
