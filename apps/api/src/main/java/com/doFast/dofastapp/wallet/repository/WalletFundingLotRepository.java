package com.doFast.dofastapp.wallet.repository;

import com.doFast.dofastapp.wallet.entity.WalletFundingLot;
import com.doFast.dofastapp.wallet.enums.WalletFundingSourceType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

public interface WalletFundingLotRepository extends JpaRepository<WalletFundingLot, Long> {

    List<WalletFundingLot> findByWallet_IdAndRemainingAmountGreaterThanOrderByCreatedAtAscIdAsc(
            Long walletId,
            BigDecimal minimumExclusive
    );

    Optional<WalletFundingLot> findByWallet_IdAndSourceTypeAndSourceReference(
            Long walletId,
            WalletFundingSourceType sourceType,
            String sourceReference
    );

    @Query("""
            SELECT COALESCE(SUM(l.remainingAmount), 0)
            FROM WalletFundingLot l
            WHERE l.wallet.id = :walletId
            """)
    BigDecimal sumRemaining(@Param("walletId") Long walletId);

    @Query("""
            SELECT COALESCE(SUM(l.remainingAmount), 0)
            FROM WalletFundingLot l
            WHERE l.wallet.id = :walletId
              AND l.withdrawable = true
            """)
    BigDecimal sumWithdrawableRemaining(@Param("walletId") Long walletId);
}
