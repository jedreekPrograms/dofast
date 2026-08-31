package com.doFast.dofastapp.payout.provider;

import com.stripe.model.Transfer;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;

class StripeConnectMoneyMovementGatewayTest {

    @Test
    void reversalRetryAfterProviderSuccessShortCircuitsFromAuthoritativeTransferState() {
        StripeConnectMoneyMovementGateway gateway = spy(new StripeConnectMoneyMovementGateway());
        Transfer transfer = matchingTransfer();
        transfer.setAmountReversed(12500L);
        transfer.setReversed(true);
        doReturn(transfer).when(gateway).retrieveTransfer("tr_123");

        assertDoesNotThrow(() -> gateway.reverseTransfer(
                "tr_123",
                12500L,
                "PLN",
                "acct_123",
                41L,
                7L,
                "payout:41:provider:transfer-reversal"
        ));

        verify(gateway).retrieveTransfer("tr_123");
    }

    @Test
    void paidSettlementGuardAcceptsCompletelyUnreversedTransfer() {
        StripeConnectMoneyMovementGateway gateway = spy(new StripeConnectMoneyMovementGateway());
        Transfer transfer = matchingTransfer();
        transfer.setAmountReversed(0L);
        transfer.setReversed(false);
        doReturn(transfer).when(gateway).retrieveTransfer("tr_123");

        assertDoesNotThrow(() -> gateway.requireTransferUnreversed(
                "tr_123", 12500L, "PLN", "acct_123", 41L, 7L
        ));
    }

    @Test
    void paidSettlementGuardRejectsEvenPartialTransferReversal() {
        StripeConnectMoneyMovementGateway gateway = spy(new StripeConnectMoneyMovementGateway());
        Transfer transfer = matchingTransfer();
        transfer.setAmountReversed(1L);
        transfer.setReversed(false);
        doReturn(transfer).when(gateway).retrieveTransfer("tr_123");

        assertThrows(IllegalStateException.class, () -> gateway.requireTransferUnreversed(
                "tr_123", 12500L, "PLN", "acct_123", 41L, 7L
        ));
    }

    private Transfer matchingTransfer() {
        Transfer transfer = new Transfer();
        transfer.setId("tr_123");
        transfer.setAmount(12500L);
        transfer.setCurrency("pln");
        transfer.setDestination("acct_123");
        transfer.setMetadata(Map.of(
                "dofastPayoutId", "41",
                "dofastUserId", "7"
        ));
        return transfer;
    }
}
