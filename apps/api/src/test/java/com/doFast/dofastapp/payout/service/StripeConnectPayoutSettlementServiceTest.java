package com.doFast.dofastapp.payout.service;

import com.doFast.dofastapp.common.exception.ConflictException;
import com.doFast.dofastapp.payout.entity.PayoutRecipientAccount;
import com.doFast.dofastapp.payout.entity.PayoutRequest;
import com.doFast.dofastapp.payout.provider.PayoutProviderSettlementCommand;
import com.doFast.dofastapp.payout.provider.PayoutProviderSettlementResult;
import com.doFast.dofastapp.payout.provider.StripeConnectMoneyMovementGateway;
import com.doFast.dofastapp.payout.repository.PayoutRecipientAccountRepository;
import com.doFast.dofastapp.payout.repository.PayoutRequestRepository;
import com.doFast.dofastapp.user.entity.User;
import com.stripe.model.Payout;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class StripeConnectPayoutSettlementServiceTest {

    @Mock private PayoutRequestRepository payoutRepository;
    @Mock private PayoutRecipientAccountRepository recipientRepository;
    @Mock private StripeConnectMoneyMovementGateway moneyGateway;
    @Mock private PayoutProviderSettlementService settlementService;

    private StripeConnectPayoutSettlementService service;
    private PayoutRequest payout;

    @BeforeEach
    void setUp() {
        service = new StripeConnectPayoutSettlementService(payoutRepository, recipientRepository, moneyGateway, settlementService);
        User user = new User();
        ReflectionTestUtils.setField(user, "id", 7L);
        payout = new PayoutRequest();
        payout.initialize(user, "payout:7:client:test", new BigDecimal("125.00"), "PLN", "stripe-connect", LocalDateTime.now());
        ReflectionTestUtils.setField(payout, "id", 41L);
        payout.startProcessing(LocalDateTime.now());
        payout.recordProviderTransferReference("tr_123");
        payout.markSubmitted("po_123", LocalDateTime.now());

        PayoutRecipientAccount recipient = new PayoutRecipientAccount();
        recipient.initialize(user, "stripe-connect", "acct_123", LocalDateTime.now());
        when(payoutRepository.findByProviderReferenceForUpdate("stripe-connect", "po_123")).thenReturn(Optional.of(payout));
        when(recipientRepository.findByUser_IdAndProviderCode(7L, "stripe-connect")).thenReturn(Optional.of(recipient));
    }

    @Test
    void failedPayoutReversesTransferBeforeWalletSettlementCanRestoreFunds() {
        Payout stripePayout = stripePayout("failed");
        when(settlementService.settle(any(PayoutProviderSettlementCommand.class))).thenReturn(PayoutProviderSettlementResult.APPLIED);

        PayoutProviderSettlementResult result = service.process(stripePayout, "evt_failed_1", "acct_123");

        assertEquals(PayoutProviderSettlementResult.APPLIED, result);
        InOrder order = inOrder(moneyGateway, settlementService);
        order.verify(moneyGateway).reverseTransfer(
                "tr_123", 12500L, "PLN", "acct_123", 41L, 7L,
                "payout:41:provider:transfer-reversal"
        );
        order.verify(settlementService).settle(any(PayoutProviderSettlementCommand.class));
    }

    @Test
    void staleFailedWebhookCannotReverseTransferOrRegressSettlement() {
        payout.recordProviderStateEventCreatedAt(200L);

        PayoutProviderSettlementResult result = service.process(stripePayout("failed"), "evt_failed_stale", "acct_123", 199L);

        assertEquals(PayoutProviderSettlementResult.STALE, result);
        assertEquals(200L, payout.getProviderStateEventCreatedAt());
        verify(moneyGateway, never()).reverseTransfer(
                org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.anyLong(), org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.anyLong(), org.mockito.ArgumentMatchers.anyLong(), org.mockito.ArgumentMatchers.any());
        verify(settlementService, never()).settle(any());
    }

    @Test
    void newerWebhookAdvancesDurableProviderOrderingWatermark() {
        payout.recordProviderStateEventCreatedAt(200L);
        when(settlementService.settle(any(PayoutProviderSettlementCommand.class))).thenReturn(PayoutProviderSettlementResult.APPLIED);

        PayoutProviderSettlementResult result = service.process(stripePayout("paid"), "evt_paid_newer", "acct_123", 201L);

        assertEquals(PayoutProviderSettlementResult.APPLIED, result);
        assertEquals(201L, payout.getProviderStateEventCreatedAt());
        verify(moneyGateway, never()).reverseTransfer(
                org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.anyLong(), org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.anyLong(), org.mockito.ArgumentMatchers.anyLong(), org.mockito.ArgumentMatchers.any());
    }

    @Test
    void paidPayoutDoesNotMoveMoneyAgain() {
        when(settlementService.settle(any(PayoutProviderSettlementCommand.class))).thenReturn(PayoutProviderSettlementResult.APPLIED);

        PayoutProviderSettlementResult result = service.process(stripePayout("paid"), "evt_paid_1", "acct_123");

        assertEquals(PayoutProviderSettlementResult.APPLIED, result);
        verify(moneyGateway, never()).reverseTransfer(
                org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.anyLong(), org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.anyLong(), org.mockito.ArgumentMatchers.anyLong(), org.mockito.ArgumentMatchers.any());
    }

    @Test
    void eventFromDifferentConnectedAccountIsRejectedBeforeSettlement() {
        assertThrows(ConflictException.class, () -> service.process(stripePayout("paid"), "evt_wrong_account", "acct_other"));
        verify(settlementService, never()).settle(any());
    }

    private Payout stripePayout(String status) {
        Payout stripePayout = new Payout();
        stripePayout.setId("po_123");
        stripePayout.setAmount(12500L);
        stripePayout.setCurrency("pln");
        stripePayout.setStatus(status);
        stripePayout.setFailureCode("account_closed");
        stripePayout.setMetadata(Map.of(
                "dofastPayoutId", "41",
                "dofastUserId", "7",
                "dofastTransferId", "tr_123"
        ));
        return stripePayout;
    }
}
