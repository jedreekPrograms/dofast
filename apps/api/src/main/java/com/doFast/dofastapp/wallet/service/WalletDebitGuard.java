package com.doFast.dofastapp.wallet.service;

import com.doFast.dofastapp.wallet.enums.WalletTransactionType;

import java.math.BigDecimal;

public interface WalletDebitGuard {
    void assertDebitAllowed(Long userId, BigDecimal amount, WalletTransactionType type);
}
