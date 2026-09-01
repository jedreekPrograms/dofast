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
import java.util.Map;

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
            validateTransfer(
                    transfer,
                    amountInCents,
                    currency,
                    recipient.getProviderAccountId(),
                    command,
                    null
            );
            transferId = transfer.getId();
            dispatchStateService.recordTransferReference(command.payoutId(), transferId);
        } else {
            transfer = moneyGateway.retrieveTransfer(transferId);
            validateTransfer(
                    transfer,
                    amountInCents,
                    currency,
                    recipient.getProviderAccountId(),
                    command,
                    transferId
            );
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

        // Even if Stripe already reports a terminal-looking status here, settlement remains webhook-authoritative.
        // Returning SUBMITTED preserves the provider reference and prevents a local wallet restore racing money
        // that may already have left the connected account.
        return PayoutDispatchResult.submitted(payout.getId());
    }

    void validateTransfer(
            Transfer transfer,
            long amountInCents,
            String currency,
            String connectedAccountId,
            PayoutDispatchCommand command,
            String alreadyPersistedTransferReference
    ) {
        if (transfer == null) {
            throw anomaly(
                    "Stripe Connect returned no transfer after the provider call",
                    "STRIPE_TRANSFER_RESPONSE_MISSING",
                    alreadyPersistedTransferReference,
                    null
            );
        }

        String returnedId = normalizedReference(transfer.getId());
        Map<String, String> metadata = transfer.getMetadata();
        boolean returnedIdentityMatches = returnedId != null
                && connectedAccountId.equals(transfer.getDestination())
                && metadata != null
                && command.payoutId().toString().equals(metadata.get("dofastPayoutId"))
                && command.userId().toString().equals(metadata.get("dofastUserId"));
        String trustedReference = normalizedReference(alreadyPersistedTransferReference);
        if (trustedReference == null && returnedIdentityMatches) {
            trustedReference = returnedId;
        }

        if (returnedId == null) {
            throw anomaly(
                    "Stripe Connect transfer response is missing an id",
                    "STRIPE_TRANSFER_ID_MISSING",
                    trustedReference,
                    null
            );
        }
        if (alreadyPersistedTransferReference != null
                && !alreadyPersistedTransferReference.equals(returnedId)) {
            throw anomaly(
                    "Stripe Connect returned a different transfer than the persisted payout transfer",
                    "STRIPE_TRANSFER_REFERENCE_MISMATCH",
                    normalizedReference(alreadyPersistedTransferReference),
                    null
            );
        }
        if (!returnedIdentityMatches) {
            throw anomaly(
                    "Stripe Connect transfer identity does not match payout request",
                    "STRIPE_TRANSFER_IDENTITY_MISMATCH",
                    normalizedReference(alreadyPersistedTransferReference),
                    null
            );
        }
        if (transfer.getAmount() == null || transfer.getAmount() != amountInCents) {
            throw anomaly(
                    "Stripe Connect transfer amount does not match payout request",
                    "STRIPE_TRANSFER_AMOUNT_MISMATCH",
                    trustedReference,
                    null
            );
        }
        if (transfer.getCurrency() == null || !currency.equalsIgnoreCase(transfer.getCurrency())) {
            throw anomaly(
                    "Stripe Connect transfer currency does not match payout request",
                    "STRIPE_TRANSFER_CURRENCY_MISMATCH",
                    trustedReference,
                    null
            );
        }
    }

    void validatePayout(
            Payout payout,
            long amountInCents,
            String currency,
            String transferId,
            PayoutDispatchCommand command
    ) {
        if (payout == null) {
            throw anomaly(
                    "Stripe Connect returned no payout after the provider call",
                    "STRIPE_PAYOUT_RESPONSE_MISSING",
                    transferId,
                    null
            );
        }

        String payoutId = normalizedReference(payout.getId());
        Map<String, String> metadata = payout.getMetadata();
        boolean identityMatches = payoutId != null
                && metadata != null
                && command.payoutId().toString().equals(metadata.get("dofastPayoutId"))
                && command.userId().toString().equals(metadata.get("dofastUserId"))
                && transferId.equals(metadata.get("dofastTransferId"));
        String trustedPayoutReference = identityMatches ? payoutId : null;

        if (payoutId == null) {
            throw anomaly(
                    "Stripe Connect payout response is missing an id",
                    "STRIPE_PAYOUT_ID_MISSING",
                    transferId,
                    null
            );
        }
        if (!identityMatches) {
            throw anomaly(
                    "Stripe Connect payout identity does not match payout request",
                    "STRIPE_PAYOUT_IDENTITY_MISMATCH",
                    transferId,
                    null
            );
        }
        if (payout.getAmount() == null || payout.getAmount() != amountInCents) {
            throw anomaly(
                    "Stripe Connect payout amount does not match payout request",
                    "STRIPE_PAYOUT_AMOUNT_MISMATCH",
                    transferId,
                    trustedPayoutReference
            );
        }
        if (payout.getCurrency() == null || !currency.equalsIgnoreCase(payout.getCurrency())) {
            throw anomaly(
                    "Stripe Connect payout currency does not match payout request",
                    "STRIPE_PAYOUT_CURRENCY_MISMATCH",
                    transferId,
                    trustedPayoutReference
            );
        }
        if (payout.getStatus() == null || payout.getStatus().isBlank()) {
            throw anomaly(
                    "Stripe Connect payout response is missing a status",
                    "STRIPE_PAYOUT_STATUS_MISSING",
                    transferId,
                    trustedPayoutReference
            );
        }
    }

    private StripeConnectPayoutResponseException anomaly(
            String message,
            String failureCode,
            String trustedTransferReference,
            String trustedPayoutReference
    ) {
        return new StripeConnectPayoutResponseException(
                message,
                failureCode,
                normalizedReference(trustedTransferReference),
                normalizedReference(trustedPayoutReference)
        );
    }

    private String normalizedReference(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String normalized = value.trim();
        return normalized.length() <= 255 ? normalized : null;
    }
}
