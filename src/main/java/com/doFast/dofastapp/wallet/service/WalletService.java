package com.doFast.dofastapp.wallet.service;

import com.doFast.dofastapp.common.exception.BusinessException;
import com.doFast.dofastapp.user.entity.User;
import com.doFast.dofastapp.wallet.dto.WalletResponse;
import com.doFast.dofastapp.wallet.entity.Wallet;
import com.doFast.dofastapp.wallet.repository.WalletRepository;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;

@Service
public class WalletService {

    private final WalletRepository walletRepository;

    public WalletService(WalletRepository walletRepository) {
        this.walletRepository = walletRepository;
    }

    public Wallet createWalletForUser(User user) {
        Wallet wallet = new Wallet(user);
        return walletRepository.save(wallet);
    }

    public WalletResponse getMyWallet(User user) {

        Wallet wallet = walletRepository.findByUser(user)
                .orElseThrow(() -> new BusinessException("Wallet nie istnieje"));

        return new WalletResponse(wallet.getBalance());
    }

    public void addMoney(User user, BigDecimal amount) {
        Wallet wallet = walletRepository.findByUser(user)
                .orElseThrow(() -> new BusinessException("Wallet nie istnieje"));

        wallet.setBalance(wallet.getBalance().add(amount));
        walletRepository.save(wallet);

    }

    public void subtractMoney(User user, BigDecimal amount){
        Wallet wallet = walletRepository.findByUser(user)
                .orElseThrow(() -> new BusinessException("Wallet nie istnieje"));

        wallet.setBalance(wallet.getBalance().subtract(amount));
        walletRepository.save(wallet);
    }

    public boolean hasEnoughMoney(User user, BigDecimal amount) {

        Wallet wallet = walletRepository.findByUser(user)
                .orElseThrow(() -> new BusinessException("Wallet nie istnieje"));

        return wallet.getBalance().compareTo(amount) >= 0;
    }
}
