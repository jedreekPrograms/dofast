package com.doFast.dofastapp.payout.service;

import com.doFast.dofastapp.common.exception.ConflictException;
import com.doFast.dofastapp.common.exception.ResourceNotFoundException;
import com.doFast.dofastapp.payout.entity.PayoutRecipientAccount;
import com.doFast.dofastapp.payout.entity.PayoutRequest;
import com.doFast.dofastapp.payout.provider.PayoutProviderSettlementCommand;
import com.doFast.dofastapp.payout.provider.PayoutProviderSettlementOutcome;
import com.doFast.dofastapp.payout.provider.PayoutProviderSettlementResult;
import com.doFast.dofastapp.payout.provider.StripeConnectMoneyMovementGateway;
import com.doFast.dofastapp.payout.repository.PayoutRecipientAccountRepository;
import com.doFast.dofastapp.payout.repository.PayoutRequestRepository;
import com.stripe.model.Payout;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Locale;
import java.util.Map;

@Service
public class StripeConnectPayoutSettlementService {

    private final PayoutRequestRepository payoutRepository;
    private final PayoutRecipientAccountRepository recipientRepository;
    private final StripeConnectMoneyMovementGateway moneyGateway;
    private final PayoutProviderSettlementService settlementService;

    public StripeConnectPayoutSettlementService(
            PayoutRequestRepository payoutRepository,
            PayoutRecipientAccountRepository recipientRepository,
            StripeConnectMoneyMovementGateway moneyGateway,
            PayoutProviderSettlementService settlementService
    ) {
        this.payoutRepository = payoutRepository;
        this.recipientRepository = recipientRepository;
        this.moneyGateway = moneyGateway;
        this.settlementService = settlementService;
    }

    @Transactional
    public PayoutProviderSettlementResult process(Payout stripePayout, String eventId, String connectedAccountId) {
        return process(stripePayout, eventId, connectedAccountId, null);
    }

    @Transactional
    public PayoutProviderSettlementResult process(
            Payout stripePayout,
            String eventId,
            String connectedAccountId,
            Long eventCreatedAt
    ) {
        PayoutProviderSettlementOutcome outcome = terminalOutcome(stripePayout);
        if (outcome == null) return null;
        if (eventId == null || eventId.isBlank() || connectedAccountId == null || connectedAccountId.isBlank()) {
            throw new IllegalStateException("Stripe Connect payout event is missing required identity");
        }
        if (eventCreatedAt != null && eventCreatedAt <= 0) {
            throw new IllegalStateException("Stripe Connect payout event timestamp is invalid");
        }
        if (stripePayout.getId() == null || stripePayout.getId().isBlank()) {
            throw new IllegalStateException("Stripe Connect payout does not contain an id");
        }

        PayoutRequest payout = payoutRepository.findByProviderReferenceForUpdate(
                        StripeConnectOnboardingService.PROVIDER_CODE,
                        stripePayout.getId())
                .orElseThrow(() -> new ResourceNotFoundException("Wypłata Stripe Connect nie istnieje"));
        PayoutRecipientAccount recipient = recipientRepository
                .findByUser_IdAndProviderCode(payout.getUser().getId(), StripeConnectOnboardingService.PROVIDER_CODE)
                .orElseThrow(() -> new ResourceNotFoundException("Konto odbiorcy Stripe Connect nie istnieje"));

        validateProviderEvent(payout, recipient, stripePayout, connectedAccountId);
        if (isStale(payout, eventCreatedAt)) {
            return PayoutProviderSettlementResult.STALE;
        }

        String failureCode = null;
        if (outcome == PayoutProviderSettlementOutcome.FAILED) {
            failureCode = failureCode(stripePayout);
            long amountInCents = payout.getAmount().movePointRight(2).longValueExact();
            moneyGateway.reverseTransfer(
                    payout.getProviderTransferReference(),
                    amountInCents,
                    payout.getCurrency(),
                    recipient.getProviderAccountId(),
                    payout.getId(),
                    payout.getUser().getId(),
                    "payout:" + payout.getId() + ":provider:transfer-reversal"
            );
        }

        PayoutProviderSettlementResult result = settlementService.settle(new PayoutProviderSettlementCommand(
                StripeConnectOnboardingService.PROVIDER_CODE,
                eventId,
                stripePayout.getId(),
                outcome,
                failureCode
        ));
        if (eventCreatedAt != null && result != PayoutProviderSettlementResult.DUPLICATE) {
            payout.recordProviderStateEventCreatedAt(eventCreatedAt);
        }
        return result;
    }

    private boolean isStale(PayoutRequest payout, Long eventCreatedAt) {
        return eventCreatedAt != null
                && payout.getProviderStateEventCreatedAt() != null
                && eventCreatedAt < payout.getProviderStateEventCreatedAt();
    }

    private PayoutProviderSettlementOutcome terminalOutcome(Payout stripePayout) {
        if (stripePayout == null || stripePayout.getStatus() == null) return null;
        return switch (stripePayout.getStatus().toLowerCase(Locale.ROOT)) {
            case "paid" -> PayoutProviderSettlementOutcome.PAID;
            case "failed", "canceled" -> PayoutProviderSettlementOutcome.FAILED;
            default -> null;
        };
    }

    private void validateProviderEvent(
            PayoutRequest payout,
            PayoutRecipientAccount recipient,
            Payout stripePayout,
            String connectedAccountId
    ) {
        long expectedAmount = payout.getAmount().movePointRight(2).longValueExact();
        Map<String, String> metadata = stripePayout.getMetadata();
        if (!recipient.getProviderAccountId().equals(connectedAccountId)
                || stripePayout.getAmount() == null || stripePayout.getAmount() != expectedAmount
                || stripePayout.getCurrency() == null || !payout.getCurrency().equalsIgnoreCase(stripePayout.getCurrency())
                || payout.getProviderTransferReference() == null
                || metadata == null
                || !payout.getId().toString().equals(metadata.get("dofastPayoutId"))
                || !payout.getUser().getId().toString().equals(metadata.get("dofastUserId"))
                || !payout.getProviderTransferReference().equals(metadata.get("dofastTransferId"))) {
            throw new ConflictException("Zdarzenie Stripe Connect nie pasuje do zapisanej wypłaty");
        }
    }

    private String failureCode(Payout stripePayout) {
        if ("canceled".equalsIgnoreCase(stripePayout.getStatus())) return "STRIPE_PAYOUT_CANCELED";
        if (stripePayout.getFailureCode() == null || stripePayout.getFailureCode().isBlank()) {
            return "STRIPE_PAYOUT_FAILED";
        }
        return "STRIPE_" + stripePayout.getFailureCode();
    }
}
