package com.doFast.dofastapp.wallet.repository;

import com.doFast.dofastapp.wallet.entity.Wallet;
import com.doFast.dofastapp.wallet.entity.WalletTransaction;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface WalletTransactionRepository extends JpaRepository<WalletTransaction, Long> {
    List<WalletTransaction> findByWalletOrderByCreatedAtDesc(Wallet wallet);
}
