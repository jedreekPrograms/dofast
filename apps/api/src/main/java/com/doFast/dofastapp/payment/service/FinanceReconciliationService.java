package com.doFast.dofastapp.payment.service;

import com.doFast.dofastapp.common.enums.TransactionStatus;
import com.doFast.dofastapp.payment.dto.FinanceReconciliationResponse;
import com.doFast.dofastapp.payment.repository.PaymentTransactionRepository;
import com.doFast.dofastapp.payment.repository.TransactionRepository;
import com.doFast.dofastapp.wallet.repository.WalletTransactionRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Service
@Transactional(readOnly = true)
public class FinanceReconciliationService {

    private final WalletTransactionRepository walletTransactionRepository;
    private final TransactionRepository transactionRepository;
    private final PaymentTransactionRepository paymentTransactionRepository;

    public FinanceReconciliationService(
            WalletTransactionRepository walletTransactionRepository,
            TransactionRepository transactionRepository,
            PaymentTransactionRepository paymentTransactionRepository
    ) {
        this.walletTransactionRepository = walletTransactionRepository;
        this.transactionRepository = transactionRepository;
        this.paymentTransactionRepository = paymentTransactionRepository;
    }

    public FinanceReconciliationResponse reconcile() {
        long walletBalanceMismatches = walletTransactionRepository.countWalletBalanceMismatches();
        long ledgerSequenceMismatches = walletTransactionRepository.countLedgerSequenceMismatches();
        long stripeLedgerMismatches = paymentTransactionRepository.countStripeLedgerMismatches();
        long heldEscrowCount = transactionRepository.countByStatus(TransactionStatus.HELD);
        BigDecimal heldEscrowAmount = transactionRepository.sumAmountByStatus(TransactionStatus.HELD);
        long processedStripePayments = paymentTransactionRepository.count();

        return new FinanceReconciliationResponse(
                walletBalanceMismatches == 0
                        && ledgerSequenceMismatches == 0
                        && stripeLedgerMismatches == 0,
                walletBalanceMismatches,
                ledgerSequenceMismatches,
                stripeLedgerMismatches,
                heldEscrowCount,
                heldEscrowAmount,
                processedStripePayments,
                LocalDateTime.now()
        );
    }
}
