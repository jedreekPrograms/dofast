package com.doFast.dofastapp.payout.service;

import com.doFast.dofastapp.common.exception.ConflictException;
import com.doFast.dofastapp.common.exception.ResourceNotFoundException;
import com.doFast.dofastapp.payout.entity.PayoutRecipientAccount;
import com.doFast.dofastapp.payout.provider.PayoutSubmittedReconciliationCommand;
import com.doFast.dofastapp.payout.provider.StripeConnectMoneyMovementGateway;
import com.doFast.dofastapp.payout.repository.PayoutRecipientAccountRepository;
import com.stripe.model.Payout;
import org.springframework.stereotype.Service;

import java.util.Locale;
import java.util.Map;

@Service
public class StripeConnectPayoutReconciliationService {

    public enum Outcome {
        PENDING,
        TERMINAL
    }

    private final PayoutRecipientAccountRepository recipientRepository;
    private final StripeConnectMoneyMovementGateway moneyGateway;
    private final StripeConnectPayoutSettlementService settlementService;

    public StripeConnectPayoutReconciliationService(
            PayoutRecipientAccountRepository recipientRepository,
            StripeConnectMoneyMovementGateway moneyGateway,
            StripeConnectPayoutSettlementService settlementService
    ) {
        this.recipientRepository = recipientRepository;
        this.moneyGateway = moneyGateway;
        this.settlementService = settlementService;
    }

    public Outcome reconcile(PayoutSubmittedReconciliationCommand command) {
        if (!StripeConnectOnboardingService.PROVIDER_CODE.equals(command.providerCode())) {
            throw new IllegalArgumentException("Unsupported payout reconciliation provider");
        }

        PayoutRecipientAccount recipient = recipientRepository
                .findByUser_IdAndProviderCode(command.userId(), StripeConnectOnboardingService.PROVIDER_CODE)
                .orElseThrow(() -> new ResourceNotFoundException("Konto odbiorcy Stripe Connect nie istnieje"));

        String connectedAccountId = recipient.getProviderAccountId();
        Payout stripePayout = moneyGateway.retrieveConnectedPayout(command.providerReference(), connectedAccountId);
        validate(command, connectedAccountId, stripePayout);

        String status = stripePayout.getStatus().trim().toLowerCase(Locale.ROOT);
        return switch (status) {
            case "pending", "in_transit" -> Outcome.PENDING;
            case "paid", "failed", "canceled" -> {
                settlementService.process(
                        stripePayout,
                        reconciliationEventId(command.payoutId(), status),
                        connectedAccountId
                );
                yield Outcome.TERMINAL;
            }
            default -> throw new ConflictException("Stripe Connect zwrócił nieobsługiwany status wypłaty");
        };
    }

    private void validate(
            PayoutSubmittedReconciliationCommand command,
            String connectedAccountId,
            Payout stripePayout
    ) {
        long expectedAmount = command.amount().movePointRight(2).longValueExact();
        Map<String, String> metadata = stripePayout == null ? null : stripePayout.getMetadata();
        if (stripePayout == null
                || stripePayout.getId() == null
                || !command.providerReference().equals(stripePayout.getId())
                || stripePayout.getStatus() == null || stripePayout.getStatus().isBlank()
                || stripePayout.getAmount() == null || stripePayout.getAmount() != expectedAmount
                || stripePayout.getCurrency() == null || !command.currency().equalsIgnoreCase(stripePayout.getCurrency())
                || connectedAccountId == null || connectedAccountId.isBlank()
                || metadata == null
                || !command.payoutId().toString().equals(metadata.get("dofastPayoutId"))
                || !command.userId().toString().equals(metadata.get("dofastUserId"))
                || !command.providerTransferReference().equals(metadata.get("dofastTransferId"))) {
            throw new ConflictException("Odczytana wypłata Stripe Connect nie pasuje do lokalnego zlecenia");
        }
    }

    private String reconciliationEventId(Long payoutId, String status) {
        return "reconcile:stripe-connect:" + payoutId + ":" + status;
    }
}
