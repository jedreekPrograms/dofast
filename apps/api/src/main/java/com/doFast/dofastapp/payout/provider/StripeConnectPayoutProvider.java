package com.doFast.dofastapp.payout.provider;

import com.doFast.dofastapp.payout.entity.PayoutRecipientAccount;
import com.doFast.dofastapp.payout.repository.PayoutRecipientAccountRepository;
import com.doFast.dofastapp.payout.service.StripeConnectOnboardingService;
import com.doFast.dofastapp.payout.service.StripeConnectPayoutDispatchStateService;
import com.stripe.model.Payout;
import com.stripe.model.Transfer;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.math.RoundingMode;
import java.util.Locale;

@Component
@ConditionalOnProperty(
        prefix = "dofast.payouts.stripe-connect",
        name = "dispatch-enabled",
        havingValue = "true"
)
public class StripeConnectPayoutProvider implements PayoutProvider {

    private final PayoutRecipientAccountRepository recipientRepository;
    private final StripeConnectGateway connectGateway;
    private final StripeConnectMoneyMovementGateway moneyGateway;
    private final StripeConnectPayoutDispatchStateService dispatchStateService;

    public StripeConnectPayoutProvider(
            PayoutRecipientAccountRepository recipientRepository,
            StripeConnectGateway connectGateway,
            StripeConnectMoneyMovementGateway moneyGateway,
            StripeConnectPayoutDispatchStateService dispatchStateService
    ) {
        this.recipientRepository = recipientRepository;
        this.connectGateway = connectGateway;
        this.moneyGateway = moneyGateway;
        this.dispatchStateService = dispatchStateService;
    }

    @Override
    public String code() {
        return StripeConnectOnboardingService.PROVIDER_CODE;
    }

    @Override
    public PayoutDispatchResult dispatch(PayoutDispatchCommand command) {
        PayoutRecipientAccount recipient = recipientRepository
                .findByUser_IdAndProviderCode(command.userId(), code())
                .orElse(null);
        if (recipient == null) {
            return PayoutDispatchResult.definitiveFailure("STRIPE_RECIPIENT_MISSING");
        }

        StripeConnectAccountState state = connectGateway.retrieveState(recipient.getProviderAccountId());
        if (!state.readyForPayout()) {
            return PayoutDispatchResult.retryableFailure("STRIPE_RECIPIENT_NOT_READY");
        }

        connectGateway.ensureManualPayoutSchedule(recipient.getProviderAccountId());

        long amountInCents = command.amount().movePointRight(2).setScale(0, RoundingMode.UNNECESSARY).longValueExact();
        String currency = command.currency().toLowerCase(Locale.ROOT);
        String transferId = dispatchStateService.transferReference(command.payoutId());

        Transfer transfer;
        if (transferId == null) {
            transfer = moneyGateway.createTransfer(
                    amountInCents,
                    currency,
                    recipient.getProviderAccountId(),
                    command.payoutId(),
                    command.userId(),
                    command.idempotencyKey() + ":transfer"
            );
            validateTransfer(transfer, amountInCents, currency, recipient.getProviderAccountId(), command);
            transferId = transfer.getId();
            dispatchStateService.recordTransferReference(command.payoutId(), transferId);
        } else {
            transfer = moneyGateway.retrieveTransfer(transferId);
            validateTransfer(transfer, amountInCents, currency, recipient.getProviderAccountId(), command);
        }

        Payout payout = moneyGateway.createConnectedPayout(
                amountInCents,
                currency,
                recipient.getProviderAccountId(),
                command.payoutId(),
                command.userId(),
                transferId,
                command.idempotencyKey() + ":payout"
        );
        validatePayout(payout, amountInCents, currency, transferId, command);

        if ("failed".equals(payout.getStatus()) || "canceled".equals(payout.getStatus())) {
            moneyGateway.reverseTransfer(
                    transferId,
                    amountInCents,
                    command.payoutId(),
                    command.idempotencyKey() + ":transfer-reversal"
            );
            return PayoutDispatchResult.definitiveFailure("STRIPE_PAYOUT_" + payout.getStatus().toUpperCase(Locale.ROOT));
        }

        return PayoutDispatchResult.submitted(payout.getId());
    }

    private void validateTransfer(
            Transfer transfer,
            long amountInCents,
            String currency,
            String connectedAccountId,
            PayoutDispatchCommand command
    ) {
        if (transfer == null || transfer.getId() == null || transfer.getId().isBlank()
                || transfer.getAmount() == null || transfer.getAmount() != amountInCents
                || transfer.getCurrency() == null || !currency.equalsIgnoreCase(transfer.getCurrency())
                || transfer.getDestination() == null || !connectedAccountId.equals(transfer.getDestination())
                || transfer.getMetadata() == null
                || !command.payoutId().toString().equals(transfer.getMetadata().get("dofastPayoutId"))
                || !command.userId().toString().equals(transfer.getMetadata().get("dofastUserId"))) {
            throw new IllegalStateException("Stripe Connect transfer does not match payout request");
        }
    }

    private void validatePayout(
            Payout payout,
            long amountInCents,
            String currency,
            String transferId,
            PayoutDispatchCommand command
    ) {
        if (payout == null || payout.getId() == null || payout.getId().isBlank()
                || payout.getAmount() == null || payout.getAmount() != amountInCents
                || payout.getCurrency() == null || !currency.equalsIgnoreCase(payout.getCurrency())
                || payout.getStatus() == null || payout.getStatus().isBlank()
                || payout.getMetadata() == null
                || !command.payoutId().toString().equals(payout.getMetadata().get("dofastPayoutId"))
                || !command.userId().toString().equals(payout.getMetadata().get("dofastUserId"))
                || !transferId.equals(payout.getMetadata().get("dofastTransferId"))) {
            throw new IllegalStateException("Stripe Connect payout does not match payout request");
        }
    }
}
