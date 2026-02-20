package com.doFast.dofastapp.payment.service;

import com.doFast.dofastapp.common.enums.TransactionStatus;
import com.doFast.dofastapp.common.exception.BusinessException;
import com.doFast.dofastapp.job.entity.Job;
import com.doFast.dofastapp.payment.entity.Transaction;
import com.doFast.dofastapp.payment.repository.TranscationRepository;
import com.doFast.dofastapp.user.entity.User;
import com.doFast.dofastapp.wallet.service.WalletService;
import org.springframework.stereotype.Service;

@Service
public class TransactionService {

    private final TranscationRepository transcationRepository;
    private final WalletService walletService;

    public TransactionService(TranscationRepository transcationRepository, WalletService walletService) {
        this.transcationRepository = transcationRepository;
        this.walletService = walletService;
    }

    public void holdMoney(Job job) {

        User payer = job.getCreatedBy();

        if (!walletService.hasEnoughMoney(payer, job.getPrice())) {
            throw new BusinessException("Brak środków na koncie");
        }

        walletService.subtractMoney(payer, job.getPrice());

        Transaction tx = new Transaction();
        tx.setJob(job);
        tx.setPayer(payer);
        tx.setAmount(job.getPrice());
        tx.setStatus(TransactionStatus.HELD);

        transcationRepository.save(tx);
    }

    public void releaseMoney(Job job, User payee) {

        Transaction tx = transcationRepository.findByJob(job)
                .orElseThrow(() -> new BusinessException("Brak transakcji"));

        walletService.addMoney(payee, tx.getAmount());

        tx.setPayee(payee);
        tx.setStatus(TransactionStatus.RELEASED);

        transcationRepository.save(tx);
    }

    public void refundMoney(Job job) {

        Transaction tx = transcationRepository.findByJob(job)
                .orElseThrow(() -> new BusinessException("Brak transakcji"));

        if (tx.getStatus() != TransactionStatus.HELD) {
            throw new BusinessException("Nie można wykonać refundu");
        }

        walletService.addMoney(tx.getPayer(), tx.getAmount());

        tx.setStatus(TransactionStatus.REFUNDED);
        transcationRepository.save(tx);
    }
}
