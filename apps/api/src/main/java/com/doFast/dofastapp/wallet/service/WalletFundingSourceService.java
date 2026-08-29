package com.doFast.dofastapp.wallet.service;

import com.doFast.dofastapp.common.exception.BusinessException;
import com.doFast.dofastapp.common.exception.ConflictException;
import com.doFast.dofastapp.wallet.entity.Wallet;
import com.doFast.dofastapp.wallet.entity.WalletFundingLot;
import com.doFast.dofastapp.wallet.entity.WalletFundingMovement;
import com.doFast.dofastapp.wallet.entity.WalletTransaction;
import com.doFast.dofastapp.wallet.enums.WalletFundingSourceType;
import com.doFast.dofastapp.wallet.enums.WalletTransactionType;
import com.doFast.dofastapp.wallet.repository.WalletFundingLotRepository;
import com.doFast.dofastapp.wallet.repository.WalletFundingMovementRepository;
import com.doFast.dofastapp.wallet.repository.WalletTransactionRepository;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Set;

@Service
public class WalletFundingSourceService {

    private static final BigDecimal ZERO = new BigDecimal("0.00");
    private static final String STRIPE_INTENT_PREFIX = "stripe:intent:";
    private static final Set<WalletTransactionType> EXPLICIT_RESTORATION_TYPES = Set.of(
            WalletTransactionType.ESCROW_ADJUSTMENT_REFUND,
            WalletTransactionType.EXPENSE_BUDGET_REFUND,
            WalletTransactionType.JOB_PUBLICATION_RELEASE,
            WalletTransactionType.PAYOUT_RESTORE,
            WalletTransactionType.CHARGEBACK_REINSTATEMENT,
            WalletTransactionType.STRIPE_REFUND_RESTORE,
            WalletTransactionType.REFUND
    );

    private final WalletFundingLotRepository lotRepository;
    private final WalletFundingMovementRepository movementRepository;
    private final WalletTransactionRepository transactionRepository;

    public WalletFundingSourceService(
            WalletFundingLotRepository lotRepository,
            WalletFundingMovementRepository movementRepository,
            WalletTransactionRepository transactionRepository
    ) {
        this.lotRepository = lotRepository;
        this.movementRepository = movementRepository;
        this.transactionRepository = transactionRepository;
    }

    public void assertCoverage(Wallet wallet) {
        if (wallet == null || wallet.getId() == null) {
            throw new IllegalArgumentException("Persisted wallet is required for funding provenance");
        }
        BigDecimal walletBalance = money(wallet.getBalance());
        BigDecimal covered = money(lotRepository.sumRemaining(wallet.getId()));
        if (covered.compareTo(walletBalance) != 0) {
            throw new ConflictException(
                    "Niespójne źródła środków portfela: saldo nie odpowiada sumie aktywnych funding lots"
            );
        }
    }

    public BigDecimal withdrawableBalance(Wallet wallet) {
        assertCoverage(wallet);
        return money(lotRepository.sumWithdrawableRemaining(wallet.getId()));
    }

    public boolean requiresExplicitRestoration(WalletTransactionType type) {
        return EXPLICIT_RESTORATION_TYPES.contains(type);
    }

    public void recordOriginCredit(WalletTransaction transaction) {
        if (transaction == null || transaction.getAmount() == null || transaction.getAmount().signum() <= 0) {
            throw new IllegalArgumentException("Positive wallet transaction is required for funding origin");
        }

        Origin origin = originFor(transaction);
        Wallet wallet = transaction.getWallet();
        if (lotRepository.findByWallet_IdAndSourceTypeAndSourceReference(
                wallet.getId(), origin.type(), origin.reference()).isPresent()) {
            throw new ConflictException("Źródło środków zostało już zapisane dla innej mutacji portfela");
        }

        WalletFundingLot lot = lotRepository.save(new WalletFundingLot(
                wallet,
                origin.type(),
                origin.reference(),
                money(transaction.getAmount()),
                origin.withdrawable(),
                LocalDateTime.now()
        ));
        movementRepository.save(new WalletFundingMovement(
                transaction,
                lot,
                money(transaction.getAmount()),
                null,
                LocalDateTime.now()
        ));
    }

    public void consumeForDebit(WalletTransaction transaction) {
        if (transaction.getType() == WalletTransactionType.STRIPE_REFUND_RESERVE) {
            throw new IllegalArgumentException("Stripe refund reserve requires an exact PaymentIntent source");
        }
        boolean withdrawableOnly = transaction.getType() == WalletTransactionType.PAYOUT_RESERVE
                || transaction.getType() == WalletTransactionType.WITHDRAW;
        consumeFromEligibleLots(transaction, null, withdrawableOnly);
    }

    public void consumeFromStripePayment(WalletTransaction transaction, String paymentIntentId) {
        String normalizedPaymentIntentId = normalizeReference(paymentIntentId, "Stripe PaymentIntent id");
        Wallet wallet = transaction.getWallet();
        WalletFundingLot lot = lotRepository.findByWallet_IdAndSourceTypeAndSourceReference(
                        wallet.getId(),
                        WalletFundingSourceType.STRIPE_PAYMENT,
                        normalizedPaymentIntentId
                )
                .orElseThrow(() -> new BusinessException(
                        "Środki z tej płatności Stripe nie są już dostępne do zwrotu"
                ));
        BigDecimal amount = money(transaction.getAmount().negate());
        if (lot.getRemainingAmount().compareTo(amount) < 0) {
            throw new BusinessException(
                    "Część środków z tej płatności Stripe została już wykorzystana i nie może zostać zwrócona"
            );
        }
        consume(transaction, lot, amount);
    }

    public void restoreFromOperation(
            WalletTransaction creditTransaction,
            String sourceOperationKey
    ) {
        WalletTransaction source = transactionRepository.findByOperationKey(sourceOperationKey)
                .orElseThrow(() -> new ConflictException("Nie znaleziono źródłowej rezerwacji środków"));
        assertSameWallet(creditTransaction, source);
        restoreFromTransactions(creditTransaction, List.of(source));
    }

    public void restoreFromJobDebits(
            WalletTransaction creditTransaction,
            Long jobId,
            Collection<WalletTransactionType> sourceTypes
    ) {
        if (jobId == null || sourceTypes == null || sourceTypes.isEmpty()) {
            throw new IllegalArgumentException("Job id and source transaction types are required for restoration");
        }
        List<WalletTransaction> sources = transactionRepository
                .findByWalletAndJobIdAndTypeInOrderByCreatedAtDescIdDesc(
                        creditTransaction.getWallet(),
                        jobId,
                        sourceTypes
                );
        restoreFromTransactions(creditTransaction, sources);
    }

    public void restoreFromOperationPrefix(
            WalletTransaction creditTransaction,
            WalletTransactionType sourceType,
            String operationPrefix
    ) {
        if (sourceType == null || operationPrefix == null || operationPrefix.isBlank()) {
            throw new IllegalArgumentException("Source transaction type and operation prefix are required");
        }
        List<WalletTransaction> sources = transactionRepository
                .findByWalletAndTypeAndOperationKeyStartingWithOrderByCreatedAtDescIdDesc(
                        creditTransaction.getWallet(),
                        sourceType,
                        operationPrefix.trim()
                );
        restoreFromTransactions(creditTransaction, sources);
    }

    private void consumeFromEligibleLots(
            WalletTransaction transaction,
            WalletFundingSourceType exactType,
            boolean withdrawableOnly
    ) {
        BigDecimal amount = money(transaction.getAmount().negate());
        List<WalletFundingLot> available = new ArrayList<>(
                lotRepository.findByWallet_IdAndRemainingAmountGreaterThanOrderByCreatedAtAscIdAsc(
                        transaction.getWallet().getId(),
                        ZERO
                )
        );
        if (exactType != null) {
            available.removeIf(lot -> lot.getSourceType() != exactType);
        }
        if (withdrawableOnly) {
            available.removeIf(lot -> !lot.isWithdrawable());
        } else {
            // Spend non-withdrawable card/legacy value first. Earned money remains available for payout
            // as long as possible, which prevents a card top-up from being converted into cash.
            available.sort(Comparator
                    .comparing(WalletFundingLot::isWithdrawable)
                    .thenComparing(WalletFundingLot::getCreatedAt)
                    .thenComparing(WalletFundingLot::getId));
        }

        BigDecimal availableAmount = available.stream()
                .map(WalletFundingLot::getRemainingAmount)
                .reduce(ZERO, BigDecimal::add);
        if (availableAmount.compareTo(amount) < 0) {
            if (withdrawableOnly) {
                throw new BusinessException("Brak środków kwalifikujących się do wypłaty");
            }
            throw new ConflictException("Saldo portfela nie ma pełnego pokrycia w funding lots");
        }

        BigDecimal remaining = amount;
        for (WalletFundingLot lot : available) {
            if (remaining.signum() <= 0) break;
            BigDecimal allocated = lot.getRemainingAmount().min(remaining).setScale(2, RoundingMode.UNNECESSARY);
            if (allocated.signum() <= 0) continue;
            consume(transaction, lot, allocated);
            remaining = remaining.subtract(allocated).setScale(2, RoundingMode.UNNECESSARY);
        }
        if (remaining.signum() != 0) {
            throw new ConflictException("Nie udało się jednoznacznie zaalokować źródeł debetu portfela");
        }
    }

    private void consume(WalletTransaction transaction, WalletFundingLot lot, BigDecimal amount) {
        lot.consume(amount);
        lotRepository.save(lot);
        movementRepository.save(new WalletFundingMovement(
                transaction,
                lot,
                amount.negate(),
                null,
                LocalDateTime.now()
        ));
    }

    private void restoreFromTransactions(
            WalletTransaction creditTransaction,
            List<WalletTransaction> sources
    ) {
        if (creditTransaction == null || creditTransaction.getAmount() == null
                || creditTransaction.getAmount().signum() <= 0) {
            throw new IllegalArgumentException("Positive restoration transaction is required");
        }

        BigDecimal remaining = money(creditTransaction.getAmount());
        for (WalletTransaction source : sources) {
            if (remaining.signum() <= 0) break;
            assertSameWallet(creditTransaction, source);
            for (WalletFundingMovement movement : movementRepository
                    .findByWalletTransaction_IdOrderByIdAsc(source.getId())) {
                if (remaining.signum() <= 0) break;
                if (movement.getAmount().signum() >= 0) continue;

                BigDecimal consumed = money(movement.getAmount().negate());
                BigDecimal alreadyRestored = money(movementRepository.sumRestoredAmount(movement.getId()));
                BigDecimal restorable = consumed.subtract(alreadyRestored).setScale(2, RoundingMode.UNNECESSARY);
                if (restorable.signum() <= 0) continue;

                BigDecimal restored = restorable.min(remaining).setScale(2, RoundingMode.UNNECESSARY);
                WalletFundingLot lot = movement.getFundingLot();
                lot.restore(restored);
                lotRepository.save(lot);
                movementRepository.save(new WalletFundingMovement(
                        creditTransaction,
                        lot,
                        restored,
                        movement,
                        LocalDateTime.now()
                ));
                remaining = remaining.subtract(restored).setScale(2, RoundingMode.UNNECESSARY);
            }
        }

        if (remaining.signum() != 0) {
            throw new ConflictException("Kwota zwrotu przekracza nierozliczone źródłowe debety portfela");
        }
    }

    private Origin originFor(WalletTransaction transaction) {
        return switch (transaction.getType()) {
            case TOP_UP, JOB_PUBLICATION_FUNDING -> new Origin(
                    WalletFundingSourceType.STRIPE_PAYMENT,
                    stripePaymentIntentReference(transaction.getOperationKey()),
                    false
            );
            case ESCROW_RELEASE, EXPENSE_REIMBURSEMENT -> new Origin(
                    WalletFundingSourceType.EARNED_JOB,
                    transaction.getOperationKey(),
                    true
            );
            default -> new Origin(
                    WalletFundingSourceType.PLATFORM_ADJUSTMENT,
                    transaction.getOperationKey(),
                    false
            );
        };
    }

    private String stripePaymentIntentReference(String operationKey) {
        if (operationKey == null || !operationKey.startsWith(STRIPE_INTENT_PREFIX)) {
            throw new ConflictException("Wpłata Stripe nie ma źródłowego PaymentIntent w operation key");
        }
        return normalizeReference(operationKey.substring(STRIPE_INTENT_PREFIX.length()), "Stripe PaymentIntent id");
    }

    private String normalizeReference(String value, String label) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(label + " is required");
        }
        String normalized = value.trim();
        if (normalized.length() > 255) {
            throw new IllegalArgumentException(label + " is too long");
        }
        return normalized;
    }

    private void assertSameWallet(WalletTransaction target, WalletTransaction source) {
        if (target == null || source == null
                || target.getWallet() == null || source.getWallet() == null
                || target.getWallet().getId() == null
                || !target.getWallet().getId().equals(source.getWallet().getId())) {
            throw new ConflictException("Źródłowy debit należy do innego portfela");
        }
    }

    private BigDecimal money(BigDecimal amount) {
        if (amount == null) return ZERO;
        try {
            return amount.setScale(2, RoundingMode.UNNECESSARY);
        } catch (ArithmeticException ex) {
            throw new IllegalStateException("Funding provenance amount has invalid precision", ex);
        }
    }

    private record Origin(WalletFundingSourceType type, String reference, boolean withdrawable) {}
}
