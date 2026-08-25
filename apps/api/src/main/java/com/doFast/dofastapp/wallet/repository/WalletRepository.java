package com.doFast.dofastapp.wallet.repository;

import com.doFast.dofastapp.user.entity.User;
import com.doFast.dofastapp.wallet.entity.Wallet;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface WalletRepository extends JpaRepository<Wallet, Long> {
    Optional<Wallet> findByUser(User User);
}
