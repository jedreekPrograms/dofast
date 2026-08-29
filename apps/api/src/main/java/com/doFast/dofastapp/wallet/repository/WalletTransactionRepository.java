package com.doFast.dofastapp.wallet.repository;

import com.doFast.dofastapp.wallet.entity.Wallet;
import com.doFast.dofastapp.wallet.entity.WalletTransaction;
import com.doFast.dofastapp.wallet.enums.WalletTransactionType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface WalletTransactionRepository extends JpaRepository<WalletTransaction, Long> {

    List<WalletTransaction> findByWalletOrderByCreatedAtDescIdDesc(Wallet wallet);

    Optional<WalletTransaction> findByOperationKey(String operationKey);

    List<WalletTransaction> findByWalletAndJobIdAndTypeInOrderByCreatedAtDescIdDesc(
            Wallet wallet,
            Long jobId,
            Collection<WalletTransactionType> types
    );

    List<WalletTransaction> findByWalletAndTypeAndOperationKeyStartingWithOrderByCreatedAtDescIdDesc(
            Wallet wallet,
            WalletTransactionType type,
            String operationKeyPrefix
    );

    @Query(value = """
            SELECT COUNT(*)
            FROM wallets w
            LEFT JOIN (
                SELECT
                    wallet_id,
                    COALESCE(SUM(amount), 0) AS ledger_balance
                FROM wallet_transactions
                GROUP BY wallet_id
            ) ledger ON ledger.wallet_id = w.id
            WHERE w.balance <> COALESCE(ledger.ledger_balance, 0)
            """, nativeQuery = true)
    long countWalletBalanceMismatches();

    @Query(value = """
            WITH ordered AS (
                SELECT
                    wallet_id,
                    amount,
                    balance_after,
                    LAG(balance_after) OVER (
                        PARTITION BY wallet_id
                        ORDER BY created_at, id
                    ) AS previous_balance
                FROM wallet_transactions
            )
            SELECT COUNT(*)
            FROM ordered
            WHERE (previous_balance IS NULL AND balance_after <> amount)
               OR (previous_balance IS NOT NULL AND balance_after <> previous_balance + amount)
            """, nativeQuery = true)
    long countLedgerSequenceMismatches();
}
