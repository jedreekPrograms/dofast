package com.doFast.dofastapp.payout.provider;

import com.doFast.dofastapp.payout.entity.PayoutRecipientAccount;
import com.doFast.dofastapp.payout.repository.PayoutRecipientAccountRepository;
import com.doFast.dofastapp.payout.service.StripeConnectPayoutDispatchStateService;
import com.doFast.dofastapp.user.entity.User;
import com.stripe.model.Payout;
import com.stripe.model.Transfer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class StripeConnectPayoutProviderTest {

    @Mock private PayoutRecipientAccountRepository recipientRepository;
    @Mock private StripeConnectGateway connectGateway;
    @Mock private StripeConnectMoneyMovementGateway moneyGateway;
    @Mock private StripeConnectPayoutDispatchStateService dispatchStateService;

    private StripeConnectPayoutProvider provider;
    private PayoutDispatchCommand command;
    private PayoutRecipientAccount recipient;

    @BeforeEach
    void setUp() {
        provider = new StripeConnectPayoutProvider(recipientRepository, connectGateway, moneyGateway, dispatchStateService);
        command = new PayoutDispatchCommand(41L, 7L, new BigDecimal("125.00"), "PLN", "stripe-connect", "payout:41:provider", 1);
        recipient = new PayoutRecipientAccount();
        recipient.initialize(new User(), "stripe-connect", "acct_123", LocalDateTime.now());
        when(recipientRepository.findByUser_IdAndProviderCode(7L, "stripe-connect")).thenReturn(Optional.of(recipient));
        when(connectGateway.retrieveState("acct_123")).thenReturn(new StripeConnectAccountState(true, true, true, false));
    }

    @Test
    void persistsTransferBeforeSubmittingConnectedPayout() {
        when(dispatchStateService.transferReference(41L)).thenReturn(null);
        when(moneyGateway.createTransfer(12500L, "pln", "acct_123", 41L, 7L, "payout:41:provider:transfer"))
                .thenReturn(transfer("tr_123"));
        when(moneyGateway.createConnectedPayout(12500L, "pln", "acct_123", 41L, 7L, "tr_123", "payout:41:provider:payout"))
                .thenReturn(payout("po_123", "pending"));

        PayoutDispatchResult result = provider.dispatch(command);

        assertTrue(result.successful());
        assertTrue(result.settlementPending());
        InOrder order = inOrder(connectGateway, moneyGateway, dispatchStateService);
        order.verify(connectGateway).ensureManualPayoutSchedule("acct_123");
        order.verify(moneyGateway).createTransfer(12500L, "pln", "acct_123", 41L, 7L, "payout:41:provider:transfer");
        order.verify(dispatchStateService).recordTransferReference(41L, "tr_123");
        order.verify(moneyGateway).createConnectedPayout(12500L, "pln", "acct_123", 41L, 7L, "tr_123", "payout:41:provider:payout");
    }

    @Test
    void retryReusesPersistedTransferInsteadOfSendingMoneyTwice() {
        when(dispatchStateService.transferReference(41L)).thenReturn("tr_123");
        when(moneyGateway.retrieveTransfer("tr_123")).thenReturn(transfer("tr_123"));
        when(moneyGateway.createConnectedPayout(12500L, "pln", "acct_123", 41L, 7L, "tr_123", "payout:41:provider:payout"))
                .thenReturn(payout("po_123", "failed"));

        PayoutDispatchResult result = provider.dispatch(command);

        assertTrue(result.successful());
        assertTrue(result.settlementPending());
        verify(moneyGateway, never()).createTransfer(org.mockito.ArgumentMatchers.anyLong(), org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any());
        verify(dispatchStateService, never()).recordTransferReference(org.mockito.ArgumentMatchers.anyLong(), org.mockito.ArgumentMatchers.any());
        verify(moneyGateway, never()).reverseTransfer(
                org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.anyLong(), org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.anyLong(), org.mockito.ArgumentMatchers.anyLong(), org.mockito.ArgumentMatchers.any());
    }

    @Test
    void transferAmountMismatchPreservesTrustedTransferIdentityAndStopsBeforePayout() {
        when(dispatchStateService.transferReference(41L)).thenReturn(null);
        Transfer transfer = transfer("tr_123");
        transfer.setAmount(12400L);
        when(moneyGateway.createTransfer(12500L, "pln", "acct_123", 41L, 7L, "payout:41:provider:transfer"))
                .thenReturn(transfer);

        StripeConnectPayoutResponseException exception = assertThrows(
                StripeConnectPayoutResponseException.class,
                () -> provider.dispatch(command)
        );

        assertEquals("STRIPE_TRANSFER_AMOUNT_MISMATCH", exception.failureCode());
        assertEquals("tr_123", exception.trustedTransferReference());
        assertNull(exception.trustedPayoutReference());
        verify(dispatchStateService, never()).recordTransferReference(41L, "tr_123");
        verify(moneyGateway, never()).createConnectedPayout(
                org.mockito.ArgumentMatchers.anyLong(), org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.anyLong(), org.mockito.ArgumentMatchers.anyLong(), org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any());
    }

    @Test
    void transferIdentityMismatchDoesNotTrustReturnedTransferId() {
        when(dispatchStateService.transferReference(41L)).thenReturn(null);
        Transfer transfer = transfer("tr_other");
        transfer.setDestination("acct_other");
        when(moneyGateway.createTransfer(12500L, "pln", "acct_123", 41L, 7L, "payout:41:provider:transfer"))
                .thenReturn(transfer);

        StripeConnectPayoutResponseException exception = assertThrows(
                StripeConnectPayoutResponseException.class,
                () -> provider.dispatch(command)
        );

        assertEquals("STRIPE_TRANSFER_IDENTITY_MISMATCH", exception.failureCode());
        assertNull(exception.trustedTransferReference());
        assertNull(exception.trustedPayoutReference());
    }

    @Test
    void payoutAmountMismatchPreservesTrustedTransferAndPayoutIdentityForReview() {
        when(dispatchStateService.transferReference(41L)).thenReturn("tr_123");
        when(moneyGateway.retrieveTransfer("tr_123")).thenReturn(transfer("tr_123"));
        Payout payout = payout("po_123", "pending");
        payout.setAmount(12400L);
        when(moneyGateway.createConnectedPayout(12500L, "pln", "acct_123", 41L, 7L, "tr_123", "payout:41:provider:payout"))
                .thenReturn(payout);

        StripeConnectPayoutResponseException exception = assertThrows(
                StripeConnectPayoutResponseException.class,
                () -> provider.dispatch(command)
        );

        assertEquals("STRIPE_PAYOUT_AMOUNT_MISMATCH", exception.failureCode());
        assertEquals("tr_123", exception.trustedTransferReference());
        assertEquals("po_123", exception.trustedPayoutReference());
    }

    @Test
    void payoutIdentityMismatchDoesNotTrustReturnedPayoutId() {
        when(dispatchStateService.transferReference(41L)).thenReturn("tr_123");
        when(moneyGateway.retrieveTransfer("tr_123")).thenReturn(transfer("tr_123"));
        Payout payout = payout("po_other", "pending");
        payout.setMetadata(Map.of("dofastPayoutId", "99", "dofastUserId", "7", "dofastTransferId", "tr_123"));
        when(moneyGateway.createConnectedPayout(12500L, "pln", "acct_123", 41L, 7L, "tr_123", "payout:41:provider:payout"))
                .thenReturn(payout);

        StripeConnectPayoutResponseException exception = assertThrows(
                StripeConnectPayoutResponseException.class,
                () -> provider.dispatch(command)
        );

        assertEquals("STRIPE_PAYOUT_IDENTITY_MISMATCH", exception.failureCode());
        assertEquals("tr_123", exception.trustedTransferReference());
        assertNull(exception.trustedPayoutReference());
    }

    private Transfer transfer(String id) {
        Transfer transfer = new Transfer();
        transfer.setId(id);
        transfer.setAmount(12500L);
        transfer.setCurrency("pln");
        transfer.setDestination("acct_123");
        transfer.setMetadata(Map.of("dofastPayoutId", "41", "dofastUserId", "7"));
        return transfer;
    }

    private Payout payout(String id, String status) {
        Payout payout = new Payout();
        payout.setId(id);
        payout.setAmount(12500L);
        payout.setCurrency("pln");
        payout.setStatus(status);
        payout.setMetadata(Map.of("dofastPayoutId", "41", "dofastUserId", "7", "dofastTransferId", "tr_123"));
        return payout;
    }
}
