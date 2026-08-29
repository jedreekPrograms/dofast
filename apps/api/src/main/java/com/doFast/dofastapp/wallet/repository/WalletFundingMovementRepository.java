package com.doFast.dofastapp.wallet.repository;

import com.doFast.dofastapp.wallet.entity.WalletFundingMovement;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.util.List;

public interface WalletFundingMovementRepository extends JpaRepository<WalletFundingMovement, Long> {

    List<WalletFundingMovement> findByWalletTransaction_IdOrderByIdAsc(Long walletTransactionId);

    @Query("""
            SELECT COALESCE(SUM(m.amount), 0)
            FROM WalletFundingMovement m
            WHERE m.restoresMovement.id = :movementId
            """)
    BigDecimal sumRestoredAmount(@Param("movementId") Long movementId);
}
