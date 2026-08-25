package com.doFast.dofastapp.wallet.service;

import com.doFast.dofastapp.common.exception.BusinessException;
import com.doFast.dofastapp.common.exception.ConflictException;
import com.doFast.dofastapp.user.entity.User;
import com.doFast.dofastapp.user.repository.UserRepository;
import com.doFast.dofastapp.wallet.dto.WalletResponse;
import com.doFast.dofastapp.wallet.entity.Wallet;
import com.doFast.dofastapp.wallet.entity.WalletTransaction;
import com.doFast.dofastapp.wallet.enums.WalletTransactionType;
import com.doFast.dofastapp.wallet.repository.WalletRepository;
import com.doFast.dofastapp.wallet.repository.WalletTransactionRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Optional;

@Service
@Transactional(readOnly = true)
public class WalletService {

    private static final int MONEY_SCALE = 2;
    private static final int OPERATION_KEY_MAX_LENGTH = 160;

    private final WalletRepository walletRepository;
    private final UserRepository userRepository;
    private final WalletTransactionRepository walletTransactionRepository;

    public WalletService(
            WalletRepository walletRepository,
            UserRepository userRepository,
            WalletTransactionRepository walletTransactionRepository
    ) {
        this.walletRepository = walletRepository;
        this.userRepository = userRepository;
        this.walletTransactionRepository = walletTransactionRepository;
    }

    @Transactional
    public void createWalletForUser(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException("Użytkownik nie istnieje"));

        walletRepository.save(new Wallet(user));
    }

    public WalletResponse getMyWallet(Long userId) {
        Wallet wallet = walletRepository.findByUser_Id(userId)
                .orElseThrow(() -> new BusinessException("Wallet nie istnieje"));

        return new WalletResponse(wallet.getBalance());
    }

    @Transactional
    public boolean credit(
            Long userId,
            BigDecimal amount,
            WalletTransactionType type,
            Long jobId,
            String operationKey
    ) {
        BigDecimal normalizedAmount = normalizePositiveAmount(amount);
        Wallet wallet = getWalletForUpdate(userId);
        return applyChange(wallet, normalizedAmount, type, jobId, normalizeOperationKey(operationKey));
    }

    @Transactional
    public boolean debit(
            Long userId,
            BigDecimal amount,
            WalletTransactionType type,
            Long jobId,
            String operationKey
    ) {
        BigDecimal normalizedAmount = normalizePositiveAmount(amount);
        Wallet wallet = getWalletForUpdate(userId);
        String normalizedOperationKey = normalizeOperationKey(operationKey);
        BigDecimal signedAmount = normalizedAmount.negate();

        if (isAlreadyApplied(wallet, type, signedAmount, normalizedOperationKey)) {
            return false;
        }

        if (wallet.getBalance().compareTo(normalizedAmount) < 0) {
            throw new BusinessException("Brak środków na koncie");
        }

        persistChange(wallet, signedAmount, type, jobId, normalizedOperationKey);
        return true;
    }

    private boolean applyChange(
            Wallet wallet,
            BigDecimal signedAmount,
            WalletTransactionType type,
            Long jobId,
            String operationKey
    ) {
        if (isAlreadyApplied(wallet, type, signedAmount, operationKey)) {
            return false;
        }

        persistChange(wallet, signedAmount, type, jobId, operationKey);
        return true;
    }

    private void persistChange(
            Wallet wallet,
            BigDecimal signedAmount,
            WalletTransactionType type,
            Long jobId,
            String operationKey
    ) {
        BigDecimal balanceAfter = wallet.getBalance().add(signedAmount).setScale(MONEY_SCALE, RoundingMode.UNNECESSARY);
        if (balanceAfter.signum() < 0) {
            throw new BusinessException("Saldo portfela nie może być ujemne");
        }

        wallet.setBalance(balanceAfter);
        walletTransactionRepository.save(
                new WalletTransaction(
                        wallet,
                        type,
                        signedAmount,
                        balanceAfter,
                        operationKey,
                        jobId
                )
        );
    }

    private boolean isAlreadyApplied(
            Wallet wallet,
            WalletTransactionType type,
            BigDecimal signedAmount,
            String operationKey
    ) {
        Optional<WalletTransaction> existing = walletTransactionRepository.findByOperationKey(operationKey);
        if (existing.isEmpty()) {
            return false;
        }

        WalletTransaction transaction = existing.get();
        boolean sameWallet = transaction.getWallet().getId().equals(wallet.getId());
        boolean sameType = transaction.getType() == type;
        boolean sameAmount = transaction.getAmount().compareTo(signedAmount) == 0;

        if (sameWallet && sameType && sameAmount) {
            return true;
        }

        throw new ConflictException("Klucz operacji portfela został już użyty dla innej operacji");
    }

    private Wallet getWalletForUpdate(Long userId) {
        return walletRepository.findByUserIdForUpdate(userId)
                .orElseThrow(() -> new BusinessException("Wallet nie istnieje"));
    }

    private BigDecimal normalizePositiveAmount(BigDecimal amount) {
        if (amount == null || amount.signum() <= 0) {
            throw new BusinessException("Kwota operacji musi być dodatnia");
        }

        try {
            return amount.setScale(MONEY_SCALE, RoundingMode.UNNECESSARY);
        } catch (ArithmeticException ex) {
            throw new BusinessException("Kwota może mieć maksymalnie dwa miejsca po przecinku");
        }
    }

    private String normalizeOperationKey(String operationKey) {
        if (operationKey == null || operationKey.isBlank()) {
            throw new IllegalArgumentException("Operation key is required for wallet mutations");
        }
        String normalized = operationKey.trim();
        if (normalized.length() > OPERATION_KEY_MAX_LENGTH) {
            throw new IllegalArgumentException("Operation key is too long");
        }
        return normalized;
    }
}
