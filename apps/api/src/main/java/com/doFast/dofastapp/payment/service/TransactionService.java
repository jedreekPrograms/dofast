package com.doFast.dofastapp.payment.service;

import com.doFast.dofastapp.common.enums.TransactionStatus;
import com.doFast.dofastapp.common.exception.BusinessException;
import com.doFast.dofastapp.common.exception.ConflictException;
import com.doFast.dofastapp.job.entity.Job;
import com.doFast.dofastapp.payment.entity.Transaction;
import com.doFast.dofastapp.payment.fee.PlatformFeePolicy;
import com.doFast.dofastapp.payment.fee.PlatformFeeQuote;
import com.doFast.dofastapp.payment.fee.PlatformRevenueService;
import com.doFast.dofastapp.payment.repository.TransactionRepository;
import com.doFast.dofastapp.user.entity.User;
import com.doFast.dofastapp.wallet.enums.WalletTransactionType;
import com.doFast.dofastapp.wallet.service.WalletService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.Set;

@Service
@Transactional
public class TransactionService {

    private static final Set<WalletTransactionType> ESCROW_SOURCE_DEBITS = Set.of(
            WalletTransactionType.ESCROW_ADJUSTMENT_LOCK,
            WalletTransactionType.ESCROW_LOCK
    );

    private final TransactionRepository transactionRepository;
    private final WalletService walletService;
    private final PlatformFeePolicy platformFeePolicy;
    private final PlatformRevenueService platformRevenueService;

    public TransactionService(
            TransactionRepository transactionRepository,
            WalletService walletService,
            PlatformFeePolicy platformFeePolicy,
            PlatformRevenueService platformRevenueService
    ) {
        this.transactionRepository = transactionRepository;
        this.walletService = walletService;
        this.platformFeePolicy = platformFeePolicy;
        this.platformRevenueService = platformRevenueService;
    }

    public void holdMoney(Job job) {
        User payer = job.getCreatedBy();
        Optional<Transaction> existing = transactionRepository.findByJobForUpdate(job);
        if (existing.isPresent()) {
            assertCompatibleHeld(existing.get(), job);
            return;
        }

        boolean debited = walletService.debit(
                payer.getId(),
                job.getPrice(),
                WalletTransactionType.ESCROW_LOCK,
                job.getId(),
                escrowOperationKey(job, "lock")
        );

        if (!debited) {
            Transaction committed = transactionRepository.findByJob(job)
                    .orElseThrow(() -> new ConflictException("Wykryto niespójny stan blokady escrow"));
            assertCompatibleHeld(committed, job);
            return;
        }

        Transaction transaction = new Transaction();
        transaction.initializeHeld(
                job,
                payer,
                job.getPrice(),
                platformFeePolicy.currentBasisPoints(),
                LocalDateTime.now()
        );
        transactionRepository.save(transaction);
    }

    @Transactional(readOnly = true)
    public BigDecimal getHeldAmount(Job job) {
        Transaction transaction = transactionRepository.findByJob(job)
                .orElseThrow(() -> new BusinessException("Brak transakcji escrow"));
        if (transaction.getStatus() != TransactionStatus.HELD) {
            throw new ConflictException("Środki escrow nie są już zablokowane");
        }
        if (!sameUser(transaction.getPayer(), job.getCreatedBy())) {
            throw new ConflictException("Escrow należy do innego płatnika");
        }
        return transaction.getAmount();
    }

    public void adjustHeldAmount(Job job, BigDecimal newAmount, Long proposalId) {
        if (newAmount == null || newAmount.signum() <= 0) {
            throw new BusinessException("Finalna kwota escrow musi być dodatnia");
        }
        if (proposalId == null) {
            throw new IllegalArgumentException("Proposal id is required for escrow adjustment");
        }

        Transaction transaction = getTransactionForUpdate(job);
        if (transaction.getStatus() != TransactionStatus.HELD) {
            throw new ConflictException("Środki escrow nie są już zablokowane");
        }
        if (!sameUser(transaction.getPayer(), job.getCreatedBy())) {
            throw new ConflictException("Escrow należy do innego płatnika");
        }

        int comparison = newAmount.compareTo(transaction.getAmount());
        if (comparison == 0) {
            return;
        }

        if (comparison > 0) {
            BigDecimal delta = newAmount.subtract(transaction.getAmount());
            boolean debited = walletService.debit(
                    transaction.getPayer().getId(),
                    delta,
                    WalletTransactionType.ESCROW_ADJUSTMENT_LOCK,
                    job.getId(),
                    proposalAdjustmentOperationKey(job, proposalId, "lock")
            );
            if (!debited) {
                throw new ConflictException("Wykryto niespójny stan dopłaty escrow");
            }
        } else {
            BigDecimal delta = transaction.getAmount().subtract(newAmount);
            boolean refunded = walletService.creditRestoringJobDebits(
                    transaction.getPayer().getId(),
                    delta,
                    WalletTransactionType.ESCROW_ADJUSTMENT_REFUND,
                    job.getId(),
                    proposalAdjustmentOperationKey(job, proposalId, "refund"),
                    ESCROW_SOURCE_DEBITS
            );
            if (!refunded) {
                throw new ConflictException("Wykryto niespójny stan zwrotu escrow");
            }
        }

        transaction.adjustHeldAmount(newAmount);
        transactionRepository.save(transaction);
    }

    public void releaseMoney(Job job, User payee) {
        Transaction transaction = getTransactionForUpdate(job);

        if (transaction.getStatus() == TransactionStatus.RELEASED) {
            if (sameUser(transaction.getPayee(), payee)) {
                return;
            }
            throw new ConflictException("Środki escrow zostały już wypłacone innemu odbiorcy");
        }
        if (transaction.getStatus() != TransactionStatus.HELD) {
            throw new ConflictException("Środki escrow nie są już zablokowane");
        }

        PlatformFeeQuote quote = platformFeePolicy.quote(
                transaction.getAmount(),
                transaction.getPlatformFeeBasisPoints()
        );
        boolean credited = walletService.credit(
                payee.getId(),
                quote.workerPayoutAmount(),
                WalletTransactionType.ESCROW_RELEASE,
                job.getId(),
                escrowOperationKey(job, "release")
        );
        if (!credited) {
            throw new ConflictException("Wykryto niespójny stan wypłaty escrow");
        }

        platformRevenueService.recordPlatformFee(transaction, job, quote.platformFeeAmount());
        transaction.releaseTo(
                payee,
                quote.platformFeeAmount(),
                quote.workerPayoutAmount(),
                LocalDateTime.now()
        );
        transactionRepository.save(transaction);
    }

    public void refundMoney(Job job) {
        Transaction transaction = getTransactionForUpdate(job);

        if (transaction.getStatus() == TransactionStatus.REFUNDED) {
            return;
        }
        if (transaction.getStatus() != TransactionStatus.HELD) {
            throw new ConflictException("Środki escrow nie są już zablokowane");
        }

        boolean credited = walletService.creditRestoringJobDebits(
                transaction.getPayer().getId(),
                transaction.getAmount(),
                WalletTransactionType.REFUND,
                job.getId(),
                escrowOperationKey(job, "refund"),
                ESCROW_SOURCE_DEBITS
        );
        if (!credited) {
            throw new ConflictException("Wykryto niespójny stan zwrotu escrow");
        }

        transaction.refund(LocalDateTime.now());
        transactionRepository.save(transaction);
    }

    public void assertHeld(Job job) {
        Transaction transaction = getTransactionForUpdate(job);
        if (transaction.getStatus() != TransactionStatus.HELD) {
            throw new BusinessException("Środki escrow nie są już zablokowane");
        }
    }

    private Transaction getTransactionForUpdate(Job job) {
        return transactionRepository.findByJobForUpdate(job)
                .orElseThrow(() -> new BusinessException("Brak transakcji escrow"));
    }

    private void assertCompatibleHeld(Transaction transaction, Job job) {
        boolean samePayer = sameUser(transaction.getPayer(), job.getCreatedBy());
        boolean sameAmount = transaction.getAmount().compareTo(job.getPrice()) == 0;
        if (transaction.getStatus() != TransactionStatus.HELD || !samePayer || !sameAmount) {
            throw new ConflictException("Dla zlecenia istnieje już inna operacja escrow");
        }
    }

    private String escrowOperationKey(Job job, String action) {
        if (job.getId() == null) {
            throw new IllegalArgumentException("Job must be persisted before escrow mutation");
        }
        return "escrow:" + job.getId() + ":" + action;
    }

    private String proposalAdjustmentOperationKey(Job job, Long proposalId, String action) {
        if (job.getId() == null) {
            throw new IllegalArgumentException("Job must be persisted before escrow mutation");
        }
        return "escrow:" + job.getId() + ":proposal:" + proposalId + ":adjust:" + action;
    }

    private boolean sameUser(User first, User second) {
        return first != null
                && second != null
                && first.getId() != null
                && first.getId().equals(second.getId());
    }
}
