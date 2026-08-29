package com.doFast.dofastapp.payout.service;

import com.doFast.dofastapp.common.exception.ConflictException;
import com.doFast.dofastapp.common.exception.ResourceNotFoundException;
import com.doFast.dofastapp.payout.entity.PayoutRequest;
import com.doFast.dofastapp.payout.enums.PayoutStatus;
import com.doFast.dofastapp.payout.repository.PayoutRequestRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class StripeConnectPayoutDispatchStateService {

    private final PayoutRequestRepository payoutRepository;

    public StripeConnectPayoutDispatchStateService(PayoutRequestRepository payoutRepository) {
        this.payoutRepository = payoutRepository;
    }

    @Transactional(readOnly = true)
    public String transferReference(Long payoutId) {
        PayoutRequest payout = payoutRepository.findById(payoutId)
                .orElseThrow(() -> new ResourceNotFoundException("Wypłata nie istnieje"));
        requireStripeConnect(payout);
        return payout.getProviderTransferReference();
    }

    @Transactional
    public void recordTransferReference(Long payoutId, String transferReference) {
        PayoutRequest payout = payoutRepository.findByIdForUpdate(payoutId)
                .orElseThrow(() -> new ResourceNotFoundException("Wypłata nie istnieje"));
        requireStripeConnect(payout);
        if (payout.getStatus() != PayoutStatus.PROCESSING) {
            throw new ConflictException("Referencję transferu można zapisać tylko podczas przetwarzania wypłaty");
        }
        String existing = payout.getProviderTransferReference();
        if (existing != null && !existing.equals(transferReference)) {
            throw new ConflictException("Wypłata ma już inną referencję transferu providera");
        }
        payout.recordProviderTransferReference(transferReference);
        payoutRepository.saveAndFlush(payout);
    }

    private void requireStripeConnect(PayoutRequest payout) {
        if (!StripeConnectOnboardingService.PROVIDER_CODE.equals(payout.getProviderCode())) {
            throw new ConflictException("Wypłata nie korzysta ze Stripe Connect");
        }
    }
}
