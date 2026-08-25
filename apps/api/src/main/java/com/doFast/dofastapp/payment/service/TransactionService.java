package com.doFast.dofastapp.payment.service;

import com.doFast.dofastapp.common.enums.TransactionStatus;
import com.doFast.dofastapp.common.exception.BusinessException;
import com.doFast.dofastapp.job.entity.Job;
import com.doFast.dofastapp.payment.entity.Transaction;
import com.doFast.dofastapp.payment.repository.TransactionRepository;
import com.doFast.dofastapp.user.entity.User;
import com.doFast.dofastapp.wallet.enums.WalletTransactionType;
import com.doFast.dofastapp.wallet.service.WalletService;
import jakarta.transaction.Transactional;
import org.springframework.stereotype.Service;

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

        if (!walletService.hasEnoughMoney(payer.getId(), job.getPrice())) {
            throw new BusinessException("Brak środków na koncie");
        }

        walletService.subtractMoney(
                payer.getId(),
                job.getPrice(),
                WalletTransactionType.ESCROW_LOCK,
                job.getId()
        );

        Transaction transaction = new Transaction();
        transaction.setJob(job);
        transaction.setPayer(payer);
        transaction.setAmount(job.getPrice());
        transaction.setStatus(TransactionStatus.HELD);

        transactionRepository.save(transaction);
    }

    public void releaseMoney(Job job, User payee) {
        Transaction transaction = getHeldTransaction(job);

        walletService.addMoney(
                payee.getId(),
                transaction.getAmount(),
                WalletTransactionType.ESCROW_RELEASE,
                job.getId()
        );

        transaction.setPayee(payee);
        transaction.setStatus(TransactionStatus.RELEASED);
        transactionRepository.save(transaction);
    }

    public void refundMoney(Job job) {
        Transaction transaction = getHeldTransaction(job);

        walletService.addMoney(
                transaction.getPayer().getId(),
                transaction.getAmount(),
                WalletTransactionType.REFUND,
                job.getId()
        );

        transaction.setStatus(TransactionStatus.REFUNDED);
        transactionRepository.save(transaction);
    }

    private Transaction getHeldTransaction(Job job) {
        Transaction transaction = transactionRepository.findByJob(job)
                .orElseThrow(() -> new BusinessException("Brak transakcji"));

        if (transaction.getStatus() != TransactionStatus.HELD) {
            throw new BusinessException("Nieprawidłowy status transakcji");
        }

        return transaction;
    }
}
