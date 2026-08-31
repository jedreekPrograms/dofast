package com.doFast.dofastapp.payout.provider;

import com.doFast.dofastapp.payment.exception.PaymentProviderException;
import com.stripe.Stripe;
import com.stripe.StripeClient;
import com.stripe.exception.StripeException;
import com.stripe.model.Payout;
import com.stripe.model.Transfer;
import com.stripe.net.RequestOptions;
import com.stripe.param.PayoutCreateParams;
import com.stripe.param.TransferCreateParams;
import com.stripe.param.TransferReversalCreateParams;
import org.springframework.stereotype.Component;

import java.util.Locale;

@Component
public class StripeConnectMoneyMovementGateway {

    public Transfer createTransfer(
            long amountInCents,
            String currency,
            String connectedAccountId,
            Long payoutId,
            Long userId,
            String idempotencyKey
    ) {
        TransferCreateParams params = TransferCreateParams.builder()
                .setAmount(amountInCents)
                .setCurrency(currency.toLowerCase(Locale.ROOT))
                .setDestination(connectedAccountId)
                .setTransferGroup("dofast-payout-" + payoutId)
                .putMetadata("dofastPayoutId", payoutId.toString())
                .putMetadata("dofastUserId", userId.toString())
                .build();
        try {
            return Transfer.create(params, RequestOptions.builder().setIdempotencyKey(idempotencyKey).build());
        } catch (StripeException ex) {
            throw new PaymentProviderException("Nie udało się przekazać środków na konto Stripe Connect", ex);
        }
    }

    public Transfer retrieveTransfer(String transferId) {
        try {
            return Transfer.retrieve(transferId);
        } catch (StripeException ex) {
            throw new PaymentProviderException("Nie udało się odczytać transferu Stripe Connect", ex);
        }
    }

    public Payout retrieveConnectedPayout(String payoutId, String connectedAccountId) {
        if (payoutId == null || payoutId.isBlank() || connectedAccountId == null || connectedAccountId.isBlank()) {
            throw new IllegalArgumentException("Stripe payout and connected account ids are required");
        }
        try {
            return Payout.retrieve(
                    payoutId.trim(),
                    RequestOptions.builder().setStripeAccount(connectedAccountId.trim()).build()
            );
        } catch (StripeException ex) {
            throw new PaymentProviderException("Nie udało się odczytać wypłaty Stripe Connect", ex);
        }
    }

    public Payout createConnectedPayout(
            long amountInCents,
            String currency,
            String connectedAccountId,
            Long payoutId,
            Long userId,
            String transferId,
            String idempotencyKey
    ) {
        PayoutCreateParams params = PayoutCreateParams.builder()
                .setAmount(amountInCents)
                .setCurrency(currency.toLowerCase(Locale.ROOT))
                .putMetadata("dofastPayoutId", payoutId.toString())
                .putMetadata("dofastUserId", userId.toString())
                .putMetadata("dofastTransferId", transferId)
                .build();
        RequestOptions options = RequestOptions.builder()
                .setStripeAccount(connectedAccountId)
                .setIdempotencyKey(idempotencyKey)
                .build();
        try {
            return Payout.create(params, options);
        } catch (StripeException ex) {
            throw new PaymentProviderException("Nie udało się zlecić wypłaty Stripe Connect", ex);
        }
    }

    public void requireTransferUnreversed(
            String transferId,
            long amountInCents,
            String currency,
            String connectedAccountId,
            Long payoutId,
            Long userId
    ) {
        Transfer transfer = retrieveTransfer(transferId);
        requireMatchingTransfer(transfer, amountInCents, currency, connectedAccountId, payoutId, userId);
        if (Boolean.TRUE.equals(transfer.getReversed()) || reversedAmount(transfer) > 0L) {
            throw new IllegalStateException("Stripe Connect transfer was already reversed; payout cannot be marked paid");
        }
    }

    public void reverseTransfer(
            String transferId,
            long amountInCents,
            String currency,
            String connectedAccountId,
            Long payoutId,
            Long userId,
            String idempotencyKey
    ) {
        Transfer transfer = retrieveTransfer(transferId);
        requireMatchingTransfer(transfer, amountInCents, currency, connectedAccountId, payoutId, userId);
        long alreadyReversed = reversedAmount(transfer);
        if (Boolean.TRUE.equals(transfer.getReversed()) || alreadyReversed >= amountInCents) {
            return;
        }
        long amountToReverse = amountInCents - alreadyReversed;
        TransferReversalCreateParams params = TransferReversalCreateParams.builder()
                .setAmount(amountToReverse)
                .putMetadata("dofastPayoutId", payoutId.toString())
                .build();
        try {
            new StripeClient(Stripe.apiKey)
                    .transfers()
                    .reversals()
                    .create(
                            transferId,
                            params,
                            RequestOptions.builder().setIdempotencyKey(idempotencyKey).build()
                    );
        } catch (StripeException ex) {
            throw new PaymentProviderException("Nie udało się odzyskać środków z nieudanej wypłaty Stripe Connect", ex);
        }
    }

    private long reversedAmount(Transfer transfer) {
        return transfer.getAmountReversed() == null ? 0L : transfer.getAmountReversed();
    }

    private void requireMatchingTransfer(
            Transfer transfer,
            long amountInCents,
            String currency,
            String connectedAccountId,
            Long payoutId,
            Long userId
    ) {
        if (transfer == null || transfer.getId() == null || transfer.getId().isBlank()
                || transfer.getAmount() == null || transfer.getAmount() != amountInCents
                || transfer.getCurrency() == null || !currency.equalsIgnoreCase(transfer.getCurrency())
                || transfer.getDestination() == null || !connectedAccountId.equals(transfer.getDestination())
                || transfer.getMetadata() == null
                || !payoutId.toString().equals(transfer.getMetadata().get("dofastPayoutId"))
                || !userId.toString().equals(transfer.getMetadata().get("dofastUserId"))) {
            throw new IllegalStateException("Stripe Connect transfer does not match payout request");
        }
    }
}
