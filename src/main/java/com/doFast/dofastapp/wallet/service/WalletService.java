package com.doFast.dofastapp.wallet.service;

import com.doFast.dofastapp.common.exception.BusinessException;
import com.doFast.dofastapp.user.entity.User;
import com.doFast.dofastapp.user.repository.UserRepository;
import com.doFast.dofastapp.wallet.dto.WalletResponse;
import com.doFast.dofastapp.wallet.entity.Wallet;
import com.doFast.dofastapp.wallet.entity.WalletTransaction;
import com.doFast.dofastapp.wallet.enums.WalletTransactionType;
import com.doFast.dofastapp.wallet.repository.WalletRepository;
import com.doFast.dofastapp.wallet.repository.WalletTransactionRepository;
import jakarta.transaction.Transactional;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;

@Service
@Transactional
public class WalletService {

    private final WalletRepository walletRepository;
    private final UserRepository userRepository;
    private final WalletTransactionRepository walletTransactionRepository;

    public WalletService(WalletRepository walletRepository, UserRepository userRepository, WalletTransactionRepository walletTransactionRepository) {
        this.walletRepository = walletRepository;
        this.userRepository = userRepository;
        this.walletTransactionRepository = walletTransactionRepository;
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

    public void addMoney(Long userId, BigDecimal amount, WalletTransactionType type, Long jobId) {

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException("Użytkownik nie istnieje"));

        Wallet wallet = walletRepository.findByUser(user)
                .orElseThrow(() -> new BusinessException("Wallet nie istnieje"));

        wallet.setBalance(wallet.getBalance().add(amount));
        //walletRepository.save(wallet);

        walletTransactionRepository.save(
                new WalletTransaction(
                        wallet,
                        type,
                        amount,
                        jobId
                )
        );
    }

    public void subtractMoney(Long userId, BigDecimal amount, WalletTransactionType type, Long jobId){

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException("Użytkownik nie istnieje"));

        Wallet wallet = walletRepository.findByUser(user)
                .orElseThrow(() -> new BusinessException("Wallet nie istnieje"));

        wallet.setBalance(wallet.getBalance().subtract(amount));

        walletTransactionRepository.save(
                new WalletTransaction(
                        wallet,
                        type,
                        amount.negate(),
                        jobId
                )
        );
    }

    public boolean hasEnoughMoney(Long userId, BigDecimal amount) {

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException("Użytkownik nie istnieje"));

        Wallet wallet = walletRepository.findByUser(user)
                .orElseThrow(() -> new BusinessException("Wallet nie istnieje"));

        return wallet.getBalance().compareTo(amount) >= 0;
    }
}
