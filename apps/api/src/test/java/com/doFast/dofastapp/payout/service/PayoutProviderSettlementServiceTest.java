package com.doFast.dofastapp.payout.service;

import com.doFast.dofastapp.common.exception.ConflictException;
import com.doFast.dofastapp.payout.entity.PayoutProviderEvent;
import com.doFast.dofastapp.payout.entity.PayoutRequest;
import com.doFast.dofastapp.payout.enums.PayoutStatus;
import com.doFast.dofastapp.payout.provider.PayoutProviderSettlementCommand;
import com.doFast.dofastapp.payout.provider.PayoutProviderSettlementOutcome;
import com.doFast.dofastapp.payout.provider.PayoutProviderSettlementResult;
import com.doFast.dofastapp.payout.repository.PayoutEventRepository;
import com.doFast.dofastapp.payout.repository.PayoutProviderEventRepository;
import com.doFast.dofastapp.payout.repository.PayoutRequestRepository;
import com.doFast.dofastapp.user.entity.User;
import com.doFast.dofastapp.user.enums.UserStatus;
import com.doFast.dofastapp.wallet.enums.WalletTransactionType;
import com.doFast.dofastapp.wallet.service.WalletService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PayoutProviderSettlementServiceTest {

    @Mock private PayoutRequestRepository payoutRepository;
    @Mock private PayoutProviderEventRepository providerEventRepository;
    @Mock private PayoutEventRepository eventRepository;
    @Mock private WalletService walletService;

    private PayoutProviderSettlementService service;

    @BeforeEach
    void setUp() {
        service = new PayoutProviderSettlementService(
                payoutRepository,
                providerEventRepository,
                eventRepository,
                walletService
        );
    }

    @Test
    void paidSettlementFinalizesSubmittedPayoutWithoutSecondWalletDebit() {
        PayoutRequest payout = submittedPayout();
        when(payoutRepository.findByProviderReferenceForUpdate("stripe-connect", "po_123"))
                .thenReturn(Optional.of(payout));
        when(providerEventRepository.existsByProviderCodeAndProviderEventId("stripe-connect", "evt_paid_1"))
                .thenReturn(false);

        var result = service.settle(command("evt_paid_1", PayoutProviderSettlementOutcome.PAID, null));

        assertEquals(PayoutProviderSettlementResult.APPLIED, result);
        assertEquals(PayoutStatus.PAID, payout.getStatus());
        assertEquals("po_123", payout.getProviderReference());
        verify(walletService, never()).credit(any(), any(), any(), any(), any());
        verify(walletService, never()).debit(any(), any(), any(), any(), any());
        verify(providerEventRepository).save(any(PayoutProviderEvent.class));
        verify(eventRepository).save(any());
    }

    @Test
    void failedSettlementRestoresReservedFundsExactlyOnce() {
        PayoutRequest payout = submittedPayout();
        when(payoutRepository.findByProviderReferenceForUpdate("stripe-connect", "po_123"))
                .thenReturn(Optional.of(payout));
        when(providerEventRepository.existsByProviderCodeAndProviderEventId("stripe-connect", "evt_failed_1"))
                .thenReturn(false);
        when(walletService.credit(
                eq(7L),
                eq(new BigDecimal("25.00")),
                eq(WalletTransactionType.PAYOUT_RESTORE),
                eq(null),
                eq("payout:41:restore")
        )).thenReturn(true);

        var result = service.settle(command(
                "evt_failed_1",
                PayoutProviderSettlementOutcome.FAILED,
                "bank account closed"
        ));

        assertEquals(PayoutProviderSettlementResult.APPLIED, result);
        assertEquals(PayoutStatus.FAILED, payout.getStatus());
        assertEquals("BANK_ACCOUNT_CLOSED", payout.getFailureCode());
        verify(walletService).credit(
                7L,
                new BigDecimal("25.00"),
                WalletTransactionType.PAYOUT_RESTORE,
                null,
                "payout:41:restore"
        );
        verify(providerEventRepository).save(any(PayoutProviderEvent.class));
    }

    @Test
    void duplicateProviderEventIsIgnoredBeforeAnyMoneyMutation() {
        PayoutRequest payout = submittedPayout();
        when(payoutRepository.findByProviderReferenceForUpdate("stripe-connect", "po_123"))
                .thenReturn(Optional.of(payout));
        when(providerEventRepository.existsByProviderCodeAndProviderEventId("stripe-connect", "evt_paid_1"))
                .thenReturn(true);

        var result = service.settle(command("evt_paid_1", PayoutProviderSettlementOutcome.PAID, null));

        assertEquals(PayoutProviderSettlementResult.DUPLICATE, result);
        assertEquals(PayoutStatus.SUBMITTED, payout.getStatus());
        verify(walletService, never()).credit(any(), any(), any(), any(), any());
        verify(providerEventRepository, never()).save(any());
        verify(eventRepository, never()).save(any());
    }

    @Test
    void repeatedMatchingTerminalEventIsAuditedWithoutSecondSettlement() {
        PayoutRequest payout = submittedPayout();
        payout.markSubmittedPaid(LocalDateTime.now());
        when(payoutRepository.findByProviderReferenceForUpdate("stripe-connect", "po_123"))
                .thenReturn(Optional.of(payout));
        when(providerEventRepository.existsByProviderCodeAndProviderEventId("stripe-connect", "evt_paid_2"))
                .thenReturn(false);

        var result = service.settle(command("evt_paid_2", PayoutProviderSettlementOutcome.PAID, null));

        assertEquals(PayoutProviderSettlementResult.ALREADY_SETTLED, result);
        assertEquals(PayoutStatus.PAID, payout.getStatus());
        verify(walletService, never()).credit(any(), any(), any(), any(), any());
        verify(providerEventRepository).save(any(PayoutProviderEvent.class));
        verify(eventRepository, never()).save(any());
    }

    @Test
    void contradictoryTerminalProviderEventNeverRestoresOrChangesFunds() {
        PayoutRequest payout = submittedPayout();
        payout.markSubmittedPaid(LocalDateTime.now());
        when(payoutRepository.findByProviderReferenceForUpdate("stripe-connect", "po_123"))
                .thenReturn(Optional.of(payout));
        when(providerEventRepository.existsByProviderCodeAndProviderEventId("stripe-connect", "evt_failed_late"))
                .thenReturn(false);

        assertThrows(
                ConflictException.class,
                () -> service.settle(command(
                        "evt_failed_late",
                        PayoutProviderSettlementOutcome.FAILED,
                        "late contradiction"
                ))
        );

        assertEquals(PayoutStatus.PAID, payout.getStatus());
        verify(walletService, never()).credit(any(), any(), any(), any(), any());
        verify(providerEventRepository, never()).save(any());
    }

    private PayoutProviderSettlementCommand command(
            String eventId,
            PayoutProviderSettlementOutcome outcome,
            String failureCode
    ) {
        return new PayoutProviderSettlementCommand(
                "stripe-connect",
                eventId,
                "po_123",
                outcome,
                failureCode
        );
    }

    private PayoutRequest submittedPayout() {
        User user = user(7L);
        PayoutRequest payout = new PayoutRequest();
        payout.initialize(
                user,
                "payout:7:client:req-12345",
                new BigDecimal("25.00"),
                "PLN",
                "stripe-connect",
                LocalDateTime.now().minusMinutes(2)
        );
        ReflectionTestUtils.setField(payout, "id", 41L);
        payout.startProcessing(LocalDateTime.now().minusMinutes(1));
        payout.markSubmitted("po_123", LocalDateTime.now().minusSeconds(30));
        return payout;
    }

    private User user(Long id) {
        User user = new User();
        ReflectionTestUtils.setField(user, "id", id);
        user.setEmail("user" + id + "@example.com");
        user.setNickname("user" + id);
        user.setPassword("hash");
        user.setStatus(UserStatus.ACTIVE);
        return user;
    }
}
