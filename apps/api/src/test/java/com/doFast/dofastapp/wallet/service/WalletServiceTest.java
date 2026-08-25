package com.doFast.dofastapp.wallet.service;

import com.doFast.dofastapp.common.exception.BusinessException;
import com.doFast.dofastapp.common.exception.ConflictException;
import com.doFast.dofastapp.user.entity.User;
import com.doFast.dofastapp.user.repository.UserRepository;
import com.doFast.dofastapp.wallet.entity.Wallet;
import com.doFast.dofastapp.wallet.entity.WalletTransaction;
import com.doFast.dofastapp.wallet.enums.WalletTransactionType;
import com.doFast.dofastapp.wallet.repository.WalletRepository;
import com.doFast.dofastapp.wallet.repository.WalletTransactionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class WalletServiceTest {

    @Mock private WalletRepository walletRepository;
    @Mock private UserRepository userRepository;
    @Mock private WalletTransactionRepository transactionRepository;

    private WalletService walletService;
    private Wallet wallet;

    @BeforeEach
    void setUp() {
        walletService = new WalletService(walletRepository, userRepository, transactionRepository);

        User user = new User("payer@example.com", "payer");
        ReflectionTestUtils.setField(user, "id", 1L);
        wallet = new Wallet(user);
        ReflectionTestUtils.setField(wallet, "id", 11L);
        wallet.setBalance(new BigDecimal("50.00"));
    }

    @Test
    void debitChecksBalanceWhileHoldingWalletLockAndWritesBalanceAfter() {
        when(walletRepository.findByUserIdForUpdate(1L)).thenReturn(Optional.of(wallet));
        when(transactionRepository.findByOperationKey("escrow:10:lock")).thenReturn(Optional.empty());

        boolean applied = walletService.debit(
                1L,
                new BigDecimal("20.00"),
                WalletTransactionType.ESCROW_LOCK,
                10L,
                "escrow:10:lock"
        );

        assertTrue(applied);
        assertEquals(new BigDecimal("30.00"), wallet.getBalance());

        ArgumentCaptor<WalletTransaction> captor = ArgumentCaptor.forClass(WalletTransaction.class);
        verify(transactionRepository).save(captor.capture());
        assertEquals(new BigDecimal("-20.00"), captor.getValue().getAmount());
        assertEquals(new BigDecimal("30.00"), captor.getValue().getBalanceAfter());
        assertEquals("escrow:10:lock", captor.getValue().getOperationKey());
    }

    @Test
    void debitCannotDriveWalletBelowZero() {
        when(walletRepository.findByUserIdForUpdate(1L)).thenReturn(Optional.of(wallet));
        when(transactionRepository.findByOperationKey("escrow:10:lock")).thenReturn(Optional.empty());

        assertThrows(
                BusinessException.class,
                () -> walletService.debit(
                        1L,
                        new BigDecimal("60.00"),
                        WalletTransactionType.ESCROW_LOCK,
                        10L,
                        "escrow:10:lock"
                )
        );

        assertEquals(new BigDecimal("50.00"), wallet.getBalance());
        verify(transactionRepository, never()).save(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void duplicateOperationKeyIsIdempotentForTheSameMutation() {
        wallet.setBalance(new BigDecimal("30.00"));
        WalletTransaction existing = new WalletTransaction(
                wallet,
                WalletTransactionType.ESCROW_LOCK,
                new BigDecimal("-20.00"),
                new BigDecimal("30.00"),
                "escrow:10:lock",
                10L
        );

        when(walletRepository.findByUserIdForUpdate(1L)).thenReturn(Optional.of(wallet));
        when(transactionRepository.findByOperationKey("escrow:10:lock")).thenReturn(Optional.of(existing));

        boolean applied = walletService.debit(
                1L,
                new BigDecimal("20.00"),
                WalletTransactionType.ESCROW_LOCK,
                10L,
                "escrow:10:lock"
        );

        assertFalse(applied);
        assertEquals(new BigDecimal("30.00"), wallet.getBalance());
        verify(transactionRepository, never()).save(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void reusedOperationKeyForDifferentMutationIsRejected() {
        WalletTransaction existing = new WalletTransaction(
                wallet,
                WalletTransactionType.TOP_UP,
                new BigDecimal("20.00"),
                new BigDecimal("50.00"),
                "shared-key",
                null
        );

        when(walletRepository.findByUserIdForUpdate(1L)).thenReturn(Optional.of(wallet));
        when(transactionRepository.findByOperationKey("shared-key")).thenReturn(Optional.of(existing));

        assertThrows(
                ConflictException.class,
                () -> walletService.debit(
                        1L,
                        new BigDecimal("20.00"),
                        WalletTransactionType.ESCROW_LOCK,
                        10L,
                        "shared-key"
                )
        );
    }
}
