package com.doFast.dofastapp.payout.service;

import com.doFast.dofastapp.common.exception.ConflictException;
import com.doFast.dofastapp.common.exception.ResourceNotFoundException;
import com.doFast.dofastapp.payout.entity.PayoutRecipientAccount;
import com.doFast.dofastapp.payout.entity.PayoutRequest;
import com.doFast.dofastapp.payout.enums.PayoutStatus;
import com.doFast.dofastapp.payout.provider.PayoutProviderSettlementCommand;
import com.doFast.dofastapp.payout.provider.PayoutProviderSettlementOutcome;
import com.doFast.dofastapp.payout.provider.PayoutProviderSettlementResult;
import com.doFast.dofastapp.payout.provider.StripeConnectMoneyMovementGateway;
import com.doFast.dofastapp.payout.repository.PayoutRecipientAccountRepository;
import com.doFast.dofastapp.payout.repository.PayoutRequestRepository;
import com.stripe.model.Payout;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
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

        PayoutRequest payout = resolvePayoutForEvent(stripePayout);
        PayoutRecipientAccount recipient = recipientRepository
                .findByUser_IdAndProviderCode(payout.getUser().getId(), StripeConnectOnboardingService.PROVIDER_CODE)
                .orElseThrow(() -> new ResourceNotFoundException("Konto odbiorcy Stripe Connect nie istnieje"));

        validateProviderEvent(payout, recipient, stripePayout, connectedAccountId);
        if (isStale(payout, eventCreatedAt)) {
            return PayoutProviderSettlementResult.STALE;
        }

        if (payout.getProviderReference() == null || payout.getStatus() == PayoutStatus.REVIEW_REQUIRED) {
            // A response-contract anomaly can preserve a trusted payout reference while deliberately
            // keeping the local request in REVIEW_REQUIRED. A later signed, fully validated terminal
            // webhook is authoritative and may recover that reviewed request into SUBMITTED before
            // applying the terminal transition. This also remains race-safe for missing references.
            payout.recoverSubmittedProviderReference(stripePayout.getId(), LocalDateTime.now());
            payoutRepository.saveAndFlush(payout);
        }
        preflightTerminalState(payout, outcome);

        long amountInCents = payout.getAmount().movePointRight(2).longValueExact();
        if (outcome == PayoutProviderSettlementOutcome.PAID && payout.getStatus() == PayoutStatus.SUBMITTED) {
            moneyGateway.requireTransferUnreversed(
                    payout.getProviderTransferReference(),
                    amountInCents,
                    payout.getCurrency(),
                    recipient.getProviderAccountId(),
                    payout.getId(),
                    payout.getUser().getId()
            );
        }

        String failureCode = null;
        if (outcome == PayoutProviderSettlementOutcome.FAILED) {
            failureCode = failureCode(stripePayout);
            if (payout.getStatus() == PayoutStatus.SUBMITTED) {
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

    private PayoutRequest resolvePayoutForEvent(Payout stripePayout) {
        PayoutRequest byReference = payoutRepository.findByProviderReferenceForUpdate(
                        StripeConnectOnboardingService.PROVIDER_CODE,
                        stripePayout.getId())
                .orElse(null);
        if (byReference != null) {
            return byReference;
        }

        Long localPayoutId = payoutIdFromMetadata(stripePayout);
        PayoutRequest payout = payoutRepository.findByIdForUpdate(localPayoutId)
                .orElseThrow(() -> new ResourceNotFoundException("Wypłata Stripe Connect nie istnieje"));
        if (!StripeConnectOnboardingService.PROVIDER_CODE.equals(payout.getProviderCode())) {
            throw new ConflictException("Zdarzenie Stripe Connect wskazuje wypłatę innego providera");
        }
        if (payout.getProviderReference() != null && !payout.getProviderReference().equals(stripePayout.getId())) {
            throw new ConflictException("Wypłata ma już inną referencję Stripe Connect");
        }
        if (payout.getProviderReference() == null
                && payout.getStatus() != PayoutStatus.PROCESSING
                && payout.getStatus() != PayoutStatus.REVIEW_REQUIRED) {
            throw new ConflictException("Brakującą referencję Stripe można odzyskać tylko dla niejednoznacznej próby dispatchu");
        }
        return payout;
    }

    private Long payoutIdFromMetadata(Payout stripePayout) {
        Map<String, String> metadata = stripePayout.getMetadata();
        String rawPayoutId = metadata == null ? null : metadata.get("dofastPayoutId");
        if (rawPayoutId == null || rawPayoutId.isBlank()) {
            throw new ResourceNotFoundException("Wypłata Stripe Connect nie zawiera lokalnej tożsamości");
        }
        try {
            long parsed = Long.parseLong(rawPayoutId);
            if (parsed <= 0) {
                throw new NumberFormatException("non-positive payout id");
            }
            return parsed;
        } catch (NumberFormatException ex) {
            throw new ConflictException("Stripe Connect zawiera nieprawidłowy identyfikator wypłaty");
        }
    }

    private boolean isStale(PayoutRequest payout, Long eventCreatedAt) {
        return eventCreatedAt != null
                && payout.getProviderStateEventCreatedAt() != null
                && eventCreatedAt < payout.getProviderStateEventCreatedAt();
    }

    private void preflightTerminalState(PayoutRequest payout, PayoutProviderSettlementOutcome outcome) {
        if (payout.getStatus() == PayoutStatus.SUBMITTED
                || (payout.getStatus() == PayoutStatus.PAID && outcome == PayoutProviderSettlementOutcome.PAID)
                || (payout.getStatus() == PayoutStatus.FAILED && outcome == PayoutProviderSettlementOutcome.FAILED)) {
            return;
        }
        throw new ConflictException("Zdarzenie providera jest sprzeczne z aktualnym stanem wypłaty");
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
