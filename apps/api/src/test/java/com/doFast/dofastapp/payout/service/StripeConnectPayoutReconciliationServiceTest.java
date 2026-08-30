package com.doFast.dofastapp.payout.service;

import com.doFast.dofastapp.common.exception.ConflictException;
import com.doFast.dofastapp.payout.entity.PayoutRecipientAccount;
import com.doFast.dofastapp.payout.provider.PayoutSubmittedReconciliationCommand;
import com.doFast.dofastapp.payout.provider.StripeConnectMoneyMovementGateway;
import com.doFast.dofastapp.payout.repository.PayoutRecipientAccountRepository;
import com.doFast.dofastapp.user.entity.User;
import com.stripe.model.Payout;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class StripeConnectPayoutReconciliationServiceTest {

    @Mock private PayoutRecipientAccountRepository recipientRepository;
    @Mock private StripeConnectMoneyMovementGateway moneyGateway;
    @Mock private StripeConnectPayoutSettlementService settlementService;

    private StripeConnectPayoutReconciliationService service;
    private PayoutSubmittedReconciliationCommand command;

    @BeforeEach
    void setUp() {
        service = new StripeConnectPayoutReconciliationService(recipientRepository, moneyGateway, settlementService);
        command = new PayoutSubmittedReconciliationCommand(
                41L,
                7L,
                new BigDecimal("125.00"),
                "PLN",
                "stripe-connect",
                "po_123",
                "tr_123"
        );

        User user = new User();
        ReflectionTestUtils.setField(user, "id", 7L);
        PayoutRecipientAccount recipient = new PayoutRecipientAccount();
        recipient.initialize(user, "stripe-connect", "acct_123", LocalDateTime.now());
        when(recipientRepository.findByUser_IdAndProviderCode(7L, "stripe-connect"))
                .thenReturn(Optional.of(recipient));
    }

    @Test
    void pendingProviderStateIsReadOnly() {
        Payout stripePayout = stripePayout("pending");
        when(moneyGateway.retrieveConnectedPayout("po_123", "acct_123")).thenReturn(stripePayout);

        var outcome = service.reconcile(command);

        assertEquals(StripeConnectPayoutReconciliationService.Outcome.PENDING, outcome);
        verify(settlementService, never()).process(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any());
    }

    @Test
    void paidProviderStateUsesExistingTerminalSettlementPath() {
        Payout stripePayout = stripePayout("paid");
        when(moneyGateway.retrieveConnectedPayout("po_123", "acct_123")).thenReturn(stripePayout);

        var outcome = service.reconcile(command);

        assertEquals(StripeConnectPayoutReconciliationService.Outcome.TERMINAL, outcome);
        verify(settlementService).process(
                stripePayout,
                "reconcile:stripe-connect:41:paid",
                "acct_123"
        );
    }

    @Test
    void failedProviderStateAlsoUsesExistingReversalAwareSettlementPath() {
        Payout stripePayout = stripePayout("failed");
        when(moneyGateway.retrieveConnectedPayout("po_123", "acct_123")).thenReturn(stripePayout);

        var outcome = service.reconcile(command);

        assertEquals(StripeConnectPayoutReconciliationService.Outcome.TERMINAL, outcome);
        verify(settlementService).process(
                stripePayout,
                "reconcile:stripe-connect:41:failed",
                "acct_123"
        );
    }

    @Test
    void mismatchedProviderAmountIsRejectedBeforeSettlement() {
        Payout stripePayout = stripePayout("paid");
        stripePayout.setAmount(12499L);
        when(moneyGateway.retrieveConnectedPayout("po_123", "acct_123")).thenReturn(stripePayout);

        assertThrows(ConflictException.class, () -> service.reconcile(command));
        verify(settlementService, never()).process(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any());
    }

    @Test
    void mismatchedTransferMetadataIsRejectedBeforeSettlement() {
        Payout stripePayout = stripePayout("paid");
        stripePayout.setMetadata(Map.of(
                "dofastPayoutId", "41",
                "dofastUserId", "7",
                "dofastTransferId", "tr_other"
        ));
        when(moneyGateway.retrieveConnectedPayout("po_123", "acct_123")).thenReturn(stripePayout);

        assertThrows(ConflictException.class, () -> service.reconcile(command));
        verify(settlementService, never()).process(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any());
    }

    @Test
    void unknownProviderStatusFailsClosed() {
        Payout stripePayout = stripePayout("mystery");
        when(moneyGateway.retrieveConnectedPayout("po_123", "acct_123")).thenReturn(stripePayout);

        assertThrows(ConflictException.class, () -> service.reconcile(command));
        verify(settlementService, never()).process(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any());
    }

    private Payout stripePayout(String status) {
        Payout payout = new Payout();
        payout.setId("po_123");
        payout.setAmount(12500L);
        payout.setCurrency("pln");
        payout.setStatus(status);
        payout.setMetadata(Map.of(
                "dofastPayoutId", "41",
                "dofastUserId", "7",
                "dofastTransferId", "tr_123"
        ));
        return payout;
    }
}
