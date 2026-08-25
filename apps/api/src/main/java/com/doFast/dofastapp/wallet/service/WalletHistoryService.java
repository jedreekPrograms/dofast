package com.doFast.dofastapp.wallet.service;

import com.doFast.dofastapp.common.exception.BusinessException;
import com.doFast.dofastapp.user.entity.User;
import com.doFast.dofastapp.wallet.dto.WalletTransactionResponse;
import com.doFast.dofastapp.wallet.entity.Wallet;
import com.doFast.dofastapp.wallet.repository.WalletRepository;
import com.doFast.dofastapp.wallet.repository.WalletTransactionRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@Transactional(readOnly = true)
public class WalletHistoryService {

    private final WalletRepository walletRepository;
    private final WalletTransactionRepository transactionRepository;

    public WalletHistoryService(
            WalletRepository walletRepository,
            WalletTransactionRepository transactionRepository
    ) {
        this.walletRepository = walletRepository;
        this.transactionRepository = transactionRepository;
    }

    public List<WalletTransactionResponse> getHistory(User user) {
        Wallet wallet = walletRepository.findByUser(user)
                .orElseThrow(() -> new BusinessException("Wallet nie istnieje"));

        return transactionRepository.findByWalletOrderByCreatedAtDescIdDesc(wallet)
                .stream()
                .map(tx -> new WalletTransactionResponse(
                        tx.getType(),
                        tx.getAmount(),
                        tx.getBalanceAfter(),
                        tx.getCreatedAt(),
                        tx.getJobId()
                ))
                .toList();
    }
}
