package com.doFast.dofastapp.wallet.service;

import com.doFast.dofastapp.common.exception.BusinessException;
import com.doFast.dofastapp.common.exception.ConflictException;
import com.doFast.dofastapp.user.entity.User;
import com.doFast.dofastapp.wallet.entity.Wallet;
import com.doFast.dofastapp.wallet.entity.WalletFundingLot;
import com.doFast.dofastapp.wallet.entity.WalletFundingMovement;
import com.doFast.dofastapp.wallet.entity.WalletTransaction;
import com.doFast.dofastapp.wallet.enums.WalletFundingSourceType;
import com.doFast.dofastapp.wallet.enums.WalletTransactionType;
import com.doFast.dofastapp.wallet.repository.WalletFundingLotRepository;
import com.doFast.dofastapp.wallet.repository.WalletFundingMovementRepository;
import com.doFast.dofastapp.wallet.repository.WalletTransactionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class WalletFundingSourceServiceTest {

    @Mock private WalletFundingLotRepository lotRepository;
    @Mock private WalletFundingMovementRepository movementRepository;
    @Mock private WalletTransactionRepository transactionRepository;

    private WalletFundingSourceService service;
    private Wallet wallet;

    @BeforeEach
    void setUp() {
        service = new WalletFundingSourceService(lotRepository, movementRepository, transactionRepository);
        wallet = new Wallet(new User("funding@example.com", "funding"));
        ReflectionTestUtils.setField(wallet, "id", 11L);
    }

    @Test
    void ordinarySpendConsumesNonWithdrawableValueBeforeEarnings() {
        WalletFundingLot stripe = lot(1L, WalletFundingSourceType.STRIPE_PAYMENT, "pi_1", "10.00", false, 1);
        WalletFundingLot earned = lot(2L, WalletFundingSourceType.EARNED_JOB, "job:7", "20.00", true, 2);
        WalletTransaction debit = transaction(WalletTransactionType.ESCROW_LOCK, "-15.00", "escrow:7:lock");
        when(lotRepository.findByWallet_IdAndRemainingAmountGreaterThanOrderByCreatedAtAscIdAsc(
                11L, new BigDecimal("0.00"))).thenReturn(List.of(earned, stripe));

        service.consumeForDebit(debit);

        assertEquals(new BigDecimal("0.00"), stripe.getRemainingAmount());
        assertEquals(new BigDecimal("15.00"), earned.getRemainingAmount());
        ArgumentCaptor<WalletFundingMovement> movements = ArgumentCaptor.forClass(WalletFundingMovement.class);
        verify(movementRepository, org.mockito.Mockito.times(2)).save(movements.capture());
        assertEquals(List.of(new BigDecimal("-10.00"), new BigDecimal("-5.00")),
                movements.getAllValues().stream().map(WalletFundingMovement::getAmount).toList());
    }

    @Test
    void payoutConsumesOnlyWithdrawableEarnings() {
        WalletFundingLot stripe = lot(1L, WalletFundingSourceType.STRIPE_PAYMENT, "pi_1", "100.00", false, 1);
        WalletFundingLot earned = lot(2L, WalletFundingSourceType.EARNED_JOB, "job:8", "20.00", true, 2);
        WalletTransaction payout = transaction(WalletTransactionType.PAYOUT_RESERVE, "-15.00", "payout:11:reserve");
        when(lotRepository.findByWallet_IdAndRemainingAmountGreaterThanOrderByCreatedAtAscIdAsc(
                11L, new BigDecimal("0.00"))).thenReturn(List.of(stripe, earned));

        service.consumeForDebit(payout);

        assertEquals(new BigDecimal("100.00"), stripe.getRemainingAmount());
        assertEquals(new BigDecimal("5.00"), earned.getRemainingAmount());
        ArgumentCaptor<WalletFundingMovement> movement = ArgumentCaptor.forClass(WalletFundingMovement.class);
        verify(movementRepository).save(movement.capture());
        assertEquals(WalletFundingSourceType.EARNED_JOB, movement.getValue().getFundingLot().getSourceType());
        assertEquals(new BigDecimal("-15.00"), movement.getValue().getAmount());
    }

    @Test
    void payoutFailsWhenTotalBalanceExistsButWithdrawableValueIsInsufficient() {
        WalletFundingLot stripe = lot(1L, WalletFundingSourceType.STRIPE_PAYMENT, "pi_1", "100.00", false, 1);
        WalletFundingLot earned = lot(2L, WalletFundingSourceType.EARNED_JOB, "job:9", "5.00", true, 2);
        WalletTransaction payout = transaction(WalletTransactionType.PAYOUT_RESERVE, "-10.00", "payout:11:reserve");
        when(lotRepository.findByWallet_IdAndRemainingAmountGreaterThanOrderByCreatedAtAscIdAsc(
                11L, new BigDecimal("0.00"))).thenReturn(List.of(stripe, earned));

        assertThrows(BusinessException.class, () -> service.consumeForDebit(payout));

        assertEquals(new BigDecimal("100.00"), stripe.getRemainingAmount());
        assertEquals(new BigDecimal("5.00"), earned.getRemainingAmount());
        verify(movementRepository, never()).save(any());
    }

    @Test
    void stripeRefundConsumesOnlyTheExactPaymentIntentLot() {
        WalletFundingLot target = lot(1L, WalletFundingSourceType.STRIPE_PAYMENT, "pi_target", "12.00", false, 1);
        WalletTransaction refundReserve = transaction(
                WalletTransactionType.STRIPE_REFUND_RESERVE,
                "-7.00",
                "stripe:refund:1:reserve"
        );
        when(lotRepository.findByWallet_IdAndSourceTypeAndSourceReference(
                11L, WalletFundingSourceType.STRIPE_PAYMENT, "pi_target")).thenReturn(Optional.of(target));

        service.consumeFromStripePayment(refundReserve, "pi_target");

        assertEquals(new BigDecimal("5.00"), target.getRemainingAmount());
        ArgumentCaptor<WalletFundingMovement> movement = ArgumentCaptor.forClass(WalletFundingMovement.class);
        verify(movementRepository).save(movement.capture());
        assertEquals(target, movement.getValue().getFundingLot());
        assertEquals(new BigDecimal("-7.00"), movement.getValue().getAmount());
    }

    @Test
    void stripeRefundFailsWhenExactPaymentIntentLotIsTooSmall() {
        WalletFundingLot target = lot(1L, WalletFundingSourceType.STRIPE_PAYMENT, "pi_target", "5.00", false, 1);
        WalletTransaction refundReserve = transaction(
                WalletTransactionType.STRIPE_REFUND_RESERVE,
                "-7.00",
                "stripe:refund:2:reserve"
        );
        when(lotRepository.findByWallet_IdAndSourceTypeAndSourceReference(
                11L, WalletFundingSourceType.STRIPE_PAYMENT, "pi_target")).thenReturn(Optional.of(target));

        assertThrows(BusinessException.class,
                () -> service.consumeFromStripePayment(refundReserve, "pi_target"));

        assertEquals(new BigDecimal("5.00"), target.getRemainingAmount());
        verify(movementRepository, never()).save(any());
    }

    @Test
    void restorationReturnsValueToTheExactPreviouslyConsumedLot() {
        WalletFundingLot stripe = lot(1L, WalletFundingSourceType.STRIPE_PAYMENT, "pi_restore", "20.00", false, 1);
        stripe.consume(new BigDecimal("7.00"));
        WalletTransaction sourceDebit = transaction(
                WalletTransactionType.STRIPE_REFUND_RESERVE,
                "-10.00",
                "stripe:refund:9:reserve"
        );
        ReflectionTestUtils.setField(sourceDebit, "id", 90L);
        WalletFundingMovement consumed = new WalletFundingMovement(
                sourceDebit, stripe, new BigDecimal("-10.00"), null, LocalDateTime.now()
        );
        ReflectionTestUtils.setField(consumed, "id", 900L);
        WalletTransaction restore = transaction(
                WalletTransactionType.STRIPE_REFUND_RESTORE,
                "7.00",
                "stripe:refund:9:restore"
        );
        when(transactionRepository.findByOperationKey("stripe:refund:9:reserve"))
                .thenReturn(Optional.of(sourceDebit));
        when(movementRepository.findByWalletTransaction_IdOrderByIdAsc(90L)).thenReturn(List.of(consumed));
        when(movementRepository.sumRestoredAmount(900L)).thenReturn(new BigDecimal("3.00"));

        service.restoreFromOperation(restore, "stripe:refund:9:reserve");

        assertEquals(new BigDecimal("20.00"), stripe.getRemainingAmount());
        ArgumentCaptor<WalletFundingMovement> movement = ArgumentCaptor.forClass(WalletFundingMovement.class);
        verify(movementRepository).save(movement.capture());
        assertEquals(new BigDecimal("7.00"), movement.getValue().getAmount());
        assertEquals(consumed, movement.getValue().getRestoresMovement());
        assertEquals(stripe, movement.getValue().getFundingLot());
    }

    @Test
    void restorationRejectsCreditLargerThanUnrestoredSourceDebits() {
        WalletFundingLot earned = lot(1L, WalletFundingSourceType.EARNED_JOB, "job:12", "10.00", true, 1);
        earned.consume(new BigDecimal("5.00"));
        WalletTransaction sourceDebit = transaction(
                WalletTransactionType.PAYOUT_RESERVE,
                "-5.00",
                "payout:12:reserve"
        );
        ReflectionTestUtils.setField(sourceDebit, "id", 91L);
        WalletFundingMovement consumed = new WalletFundingMovement(
                sourceDebit, earned, new BigDecimal("-5.00"), null, LocalDateTime.now()
        );
        ReflectionTestUtils.setField(consumed, "id", 901L);
        WalletTransaction restore = transaction(
                WalletTransactionType.PAYOUT_RESTORE,
                "6.00",
                "payout:12:restore"
        );
        when(transactionRepository.findByOperationKey("payout:12:reserve")).thenReturn(Optional.of(sourceDebit));
        when(movementRepository.findByWalletTransaction_IdOrderByIdAsc(91L)).thenReturn(List.of(consumed));
        when(movementRepository.sumRestoredAmount(901L)).thenReturn(new BigDecimal("0.00"));

        assertThrows(ConflictException.class,
                () -> service.restoreFromOperation(restore, "payout:12:reserve"));
    }

    @Test
    void coverageMismatchFailsClosed() {
        wallet.setBalance(new BigDecimal("10.00"));
        when(lotRepository.sumRemaining(11L)).thenReturn(new BigDecimal("9.99"));

        ConflictException exception = assertThrows(ConflictException.class, () -> service.assertCoverage(wallet));

        assertTrue(exception.getMessage().contains("Niespójne źródła środków"));
    }

    private WalletFundingLot lot(
            long id,
            WalletFundingSourceType sourceType,
            String reference,
            String amount,
            boolean withdrawable,
            int ageMinutes
    ) {
        WalletFundingLot lot = new WalletFundingLot(
                wallet,
                sourceType,
                reference,
                new BigDecimal(amount),
                withdrawable,
                LocalDateTime.now().minusMinutes(ageMinutes)
        );
        ReflectionTestUtils.setField(lot, "id", id);
        return lot;
    }

    private WalletTransaction transaction(WalletTransactionType type, String amount, String operationKey) {
        return new WalletTransaction(
                wallet,
                type,
                new BigDecimal(amount),
                BigDecimal.ZERO,
                operationKey,
                null
        );
    }
}
