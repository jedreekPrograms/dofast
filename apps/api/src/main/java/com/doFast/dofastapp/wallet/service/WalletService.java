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
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

@Service
@Transactional(readOnly = true)
public class WalletService {

    private static final int MONEY_SCALE = 2;
    private static final int OPERATION_KEY_MAX_LENGTH = 160;

    private final WalletRepository walletRepository;
    private final UserRepository userRepository;
    private final WalletTransactionRepository walletTransactionRepository;
    private final WalletFundingSourceService fundingSourceService;
    private final List<WalletDebitGuard> debitGuards;

    @Autowired
    public WalletService(
            WalletRepository walletRepository,
            UserRepository userRepository,
            WalletTransactionRepository walletTransactionRepository,
            WalletFundingSourceService fundingSourceService,
            List<WalletDebitGuard> debitGuards
    ) {
        this.walletRepository = walletRepository;
        this.userRepository = userRepository;
        this.walletTransactionRepository = walletTransactionRepository;
        this.fundingSourceService = fundingSourceService;
        this.debitGuards = List.copyOf(debitGuards);
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
        fundingSourceService.assertCoverage(wallet);
        return new WalletResponse(wallet.getBalance());
    }

    public BigDecimal getWithdrawableBalance(Long userId) {
        Wallet wallet = walletRepository.findByUser_Id(userId)
                .orElseThrow(() -> new BusinessException("Wallet nie istnieje"));
        return fundingSourceService.withdrawableBalance(wallet);
    }

    @Transactional
    public BigDecimal getBalanceForUpdate(Long userId) {
        Wallet wallet = getWalletForUpdate(userId);
        fundingSourceService.assertCoverage(wallet);
        return wallet.getBalance();
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
        String normalizedOperationKey = normalizeOperationKey(operationKey);
        fundingSourceService.assertCoverage(wallet);

        if (isAlreadyApplied(wallet, type, normalizedAmount, normalizedOperationKey)) {
            return false;
        }
        if (fundingSourceService.requiresExplicitRestoration(type)) {
            throw new IllegalArgumentException("Wallet transaction type requires explicit source restoration: " + type);
        }

        WalletTransaction transaction = persistChange(
                wallet, normalizedAmount, type, jobId, normalizedOperationKey
        );
        fundingSourceService.recordOriginCredit(transaction);
        fundingSourceService.assertCoverage(wallet);
        return true;
    }

    @Transactional
    public boolean debit(
            Long userId,
            BigDecimal amount,
            WalletTransactionType type,
            Long jobId,
            String operationKey
    ) {
        if (type == WalletTransactionType.STRIPE_REFUND_RESERVE) {
            throw new IllegalArgumentException("Stripe refund reserve requires debitFromStripePayment");
        }
        return debitInternal(userId, amount, type, jobId, operationKey, null);
    }

    @Transactional
    public boolean debitFromStripePayment(
            Long userId,
            BigDecimal amount,
            WalletTransactionType type,
            Long jobId,
            String operationKey,
            String stripePaymentIntentId
    ) {
        if (type != WalletTransactionType.STRIPE_REFUND_RESERVE) {
            throw new IllegalArgumentException("Exact Stripe source debit is reserved for Stripe refunds");
        }
        return debitInternal(userId, amount, type, jobId, operationKey, stripePaymentIntentId);
    }

    @Transactional
    public boolean creditRestoringOperation(
            Long userId,
            BigDecimal amount,
            WalletTransactionType type,
            Long jobId,
            String operationKey,
            String sourceOperationKey
    ) {
        return creditRestoration(
                userId,
                amount,
                type,
                jobId,
                operationKey,
                transaction -> fundingSourceService.restoreFromOperation(
                        transaction,
                        normalizeOperationKey(sourceOperationKey)
                )
        );
    }

    @Transactional
    public boolean creditRestoringJobDebits(
            Long userId,
            BigDecimal amount,
            WalletTransactionType type,
            Long jobId,
            String operationKey,
            Collection<WalletTransactionType> sourceTypes
    ) {
        return creditRestoration(
                userId,
                amount,
                type,
                jobId,
                operationKey,
                transaction -> fundingSourceService.restoreFromJobDebits(transaction, jobId, sourceTypes)
        );
    }

    @Transactional
    public boolean creditRestoringOperationPrefix(
            Long userId,
            BigDecimal amount,
            WalletTransactionType type,
            Long jobId,
            String operationKey,
            WalletTransactionType sourceType,
            String sourceOperationPrefix
    ) {
        return creditRestoration(
                userId,
                amount,
                type,
                jobId,
                operationKey,
                transaction -> fundingSourceService.restoreFromOperationPrefix(
                        transaction,
                        sourceType,
                        sourceOperationPrefix
                )
        );
    }

    private boolean debitInternal(
            Long userId,
            BigDecimal amount,
            WalletTransactionType type,
            Long jobId,
            String operationKey,
            String stripePaymentIntentId
    ) {
        BigDecimal normalizedAmount = normalizePositiveAmount(amount);
        Wallet wallet = getWalletForUpdate(userId);
        String normalizedOperationKey = normalizeOperationKey(operationKey);
        BigDecimal signedAmount = normalizedAmount.negate();
        fundingSourceService.assertCoverage(wallet);

        if (isAlreadyApplied(wallet, type, signedAmount, normalizedOperationKey)) {
            return false;
        }

        for (WalletDebitGuard debitGuard : debitGuards) {
            debitGuard.assertDebitAllowed(userId, normalizedAmount, type);
        }

        if (wallet.getBalance().compareTo(normalizedAmount) < 0) {
            throw new BusinessException("Brak środków na koncie");
        }

        WalletTransaction transaction = persistChange(
                wallet, signedAmount, type, jobId, normalizedOperationKey
        );
        if (stripePaymentIntentId == null) {
            fundingSourceService.consumeForDebit(transaction);
        } else {
            fundingSourceService.consumeFromStripePayment(transaction, stripePaymentIntentId);
        }
        fundingSourceService.assertCoverage(wallet);
        return true;
    }

    private boolean creditRestoration(
            Long userId,
            BigDecimal amount,
            WalletTransactionType type,
            Long jobId,
            String operationKey,
            FundingRestoration restoration
    ) {
        if (!fundingSourceService.requiresExplicitRestoration(type)) {
            throw new IllegalArgumentException("Wallet transaction type is not a source restoration: " + type);
        }

        BigDecimal normalizedAmount = normalizePositiveAmount(amount);
        Wallet wallet = getWalletForUpdate(userId);
        String normalizedOperationKey = normalizeOperationKey(operationKey);
        fundingSourceService.assertCoverage(wallet);

        if (isAlreadyApplied(wallet, type, normalizedAmount, normalizedOperationKey)) {
            return false;
        }

        WalletTransaction transaction = persistChange(
                wallet, normalizedAmount, type, jobId, normalizedOperationKey
        );
        restoration.restore(transaction);
        fundingSourceService.assertCoverage(wallet);
        return true;
    }

    private WalletTransaction persistChange(
            Wallet wallet,
            BigDecimal signedAmount,
            WalletTransactionType type,
            Long jobId,
            String operationKey
    ) {
        BigDecimal balanceAfter = wallet.getBalance().add(signedAmount)
                .setScale(MONEY_SCALE, RoundingMode.UNNECESSARY);
        if (balanceAfter.signum() < 0) {
            throw new BusinessException("Saldo portfela nie może być ujemne");
        }

        wallet.setBalance(balanceAfter);
        return walletTransactionRepository.saveAndFlush(
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

    @FunctionalInterface
    private interface FundingRestoration {
        void restore(WalletTransaction transaction);
    }
}
