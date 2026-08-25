package com.doFast.dofastapp.payment.service;

import com.doFast.dofastapp.common.enums.TransactionStatus;
import com.doFast.dofastapp.common.exception.BusinessException;
import com.doFast.dofastapp.common.exception.ConflictException;
import com.doFast.dofastapp.job.entity.Job;
import com.doFast.dofastapp.payment.entity.Transaction;
import com.doFast.dofastapp.payment.repository.TransactionRepository;
import com.doFast.dofastapp.user.entity.User;
import com.doFast.dofastapp.wallet.enums.WalletTransactionType;
import com.doFast.dofastapp.wallet.service.WalletService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Optional;

@Service
@Transactional
public class TransactionService {

    private final TransactionRepository transactionRepository;
    private final WalletService walletService;

    public TransactionService(TransactionRepository transactionRepository, WalletService walletService) {
        this.transactionRepository = transactionRepository;
        this.walletService = walletService;
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
        transaction.initializeHeld(job, payer, job.getPrice(), LocalDateTime.now());
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

        boolean credited = walletService.credit(
                payee.getId(),
                transaction.getAmount(),
                WalletTransactionType.ESCROW_RELEASE,
                job.getId(),
                escrowOperationKey(job, "release")
        );
        if (!credited) {
            throw new ConflictException("Wykryto niespójny stan wypłaty escrow");
        }

        transaction.releaseTo(payee, LocalDateTime.now());
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

        boolean credited = walletService.credit(
                transaction.getPayer().getId(),
                transaction.getAmount(),
                WalletTransactionType.REFUND,
                job.getId(),
                escrowOperationKey(job, "refund")
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

    private boolean sameUser(User first, User second) {
        return first != null
                && second != null
                && first.getId() != null
                && first.getId().equals(second.getId());
    }
}
