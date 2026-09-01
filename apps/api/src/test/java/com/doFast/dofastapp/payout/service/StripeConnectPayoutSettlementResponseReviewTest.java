package com.doFast.dofastapp.payout.service;

import com.doFast.dofastapp.payout.entity.PayoutRecipientAccount;
import com.doFast.dofastapp.payout.entity.PayoutRequest;
import com.doFast.dofastapp.payout.enums.PayoutStatus;
import com.doFast.dofastapp.payout.provider.PayoutProviderSettlementCommand;
import com.doFast.dofastapp.payout.provider.PayoutProviderSettlementResult;
import com.doFast.dofastapp.payout.provider.StripeConnectMoneyMovementGateway;
import com.doFast.dofastapp.payout.repository.PayoutRecipientAccountRepository;
import com.doFast.dofastapp.payout.repository.PayoutRequestRepository;
import com.doFast.dofastapp.user.entity.User;
import com.stripe.model.Payout;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class StripeConnectPayoutSettlementResponseReviewTest {

    @Test
    void signedTerminalWebhookRecoversReviewedKnownPayoutReference() {
        PayoutRequestRepository payoutRepository = mock(PayoutRequestRepository.class);
        PayoutRecipientAccountRepository recipientRepository = mock(PayoutRecipientAccountRepository.class);
        StripeConnectMoneyMovementGateway moneyGateway = mock(StripeConnectMoneyMovementGateway.class);
        PayoutProviderSettlementService settlementService = mock(PayoutProviderSettlementService.class);
        StripeConnectPayoutSettlementService service = new StripeConnectPayoutSettlementService(
                payoutRepository,
                recipientRepository,
                moneyGateway,
                settlementService
        );

        User user = new User();
        ReflectionTestUtils.setField(user, "id", 7L);
        PayoutRequest payout = new PayoutRequest();
        payout.initialize(
                user,
                "payout:7:client:review",
                new BigDecimal("125.00"),
                "PLN",
                "stripe-connect",
                LocalDateTime.of(2026, 8, 31, 20, 0)
        );
        ReflectionTestUtils.setField(payout, "id", 41L);
        payout.startProcessing(LocalDateTime.of(2026, 8, 31, 20, 1));
        payout.recordProviderResponseForReview(
                "tr_123",
                "po_123",
                "STRIPE_PAYOUT_AMOUNT_MISMATCH",
                LocalDateTime.of(2026, 8, 31, 20, 2)
        );

        PayoutRecipientAccount recipient = new PayoutRecipientAccount();
        recipient.initialize(user, "stripe-connect", "acct_123", LocalDateTime.of(2026, 8, 31, 19, 0));
        when(payoutRepository.findByProviderReferenceForUpdate("stripe-connect", "po_123"))
                .thenReturn(Optional.of(payout));
        when(recipientRepository.findByUser_IdAndProviderCode(7L, "stripe-connect"))
                .thenReturn(Optional.of(recipient));
        when(settlementService.settle(any(PayoutProviderSettlementCommand.class)))
                .thenReturn(PayoutProviderSettlementResult.APPLIED);

        Payout stripePayout = new Payout();
        stripePayout.setId("po_123");
        stripePayout.setAmount(12500L);
        stripePayout.setCurrency("pln");
        stripePayout.setStatus("paid");
        stripePayout.setMetadata(Map.of(
                "dofastPayoutId", "41",
                "dofastUserId", "7",
                "dofastTransferId", "tr_123"
        ));

        PayoutProviderSettlementResult result = service.process(
                stripePayout,
                "evt_paid_review_recovery",
                "acct_123",
                201L
        );

        assertEquals(PayoutProviderSettlementResult.APPLIED, result);
        assertEquals(PayoutStatus.SUBMITTED, payout.getStatus());
        assertEquals("po_123", payout.getProviderReference());
        assertNull(payout.getFailureCode());
        assertEquals(201L, payout.getProviderStateEventCreatedAt());
        verify(payoutRepository).saveAndFlush(payout);
        verify(moneyGateway).requireTransferUnreversed("tr_123", 12500L, "PLN", "acct_123", 41L, 7L);
    }
}
