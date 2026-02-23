package com.doFast.dofastapp.wallet.service;

import com.doFast.dofastapp.common.exception.BusinessException;
import com.doFast.dofastapp.user.entity.User;
import com.doFast.dofastapp.user.repository.UserRepository;
import com.doFast.dofastapp.wallet.dto.WalletResponse;
import com.doFast.dofastapp.wallet.entity.Wallet;
import com.doFast.dofastapp.wallet.repository.WalletRepository;
import jakarta.transaction.Transactional;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;

@Service
@Transactional
public class WalletService {

    private final WalletRepository walletRepository;
    private final UserRepository userRepository;

    public WalletService(WalletRepository walletRepository, UserRepository userRepository) {
        this.walletRepository = walletRepository;
        this.userRepository = userRepository;
    }

    public void createWalletForUser(Long userId) {

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException("Użytkownik nie istnieje"));

        Wallet wallet = new Wallet(user);
        walletRepository.save(wallet);
    }

    public WalletResponse getMyWallet(Long userId) {

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException("Użytkownik nie istnieje"));

        Wallet wallet = walletRepository.findByUser(user)
                .orElseThrow(() -> new BusinessException("Wallet nie istnieje"));

        return new WalletResponse(wallet.getBalance());
    }

    public void addMoney(Long userId, BigDecimal amount) {

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException("Użytkownik nie istnieje"));

        Wallet wallet = walletRepository.findByUser(user)
                .orElseThrow(() -> new BusinessException("Wallet nie istnieje"));

        wallet.setBalance(wallet.getBalance().add(amount));
        walletRepository.save(wallet);

    }

    public void subtractMoney(Long userId, BigDecimal amount){

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException("Użytkownik nie istnieje"));

        Wallet wallet = walletRepository.findByUser(user)
                .orElseThrow(() -> new BusinessException("Wallet nie istnieje"));

        wallet.setBalance(wallet.getBalance().subtract(amount));
        walletRepository.save(wallet);
    }

    public boolean hasEnoughMoney(Long userId, BigDecimal amount) {

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException("Użytkownik nie istnieje"));

        Wallet wallet = walletRepository.findByUser(user)
                .orElseThrow(() -> new BusinessException("Wallet nie istnieje"));

        return wallet.getBalance().compareTo(amount) >= 0;
    }
}
