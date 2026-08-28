package com.doFast.dofastapp.payment.fee;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.util.Optional;

public interface PlatformRevenueEntryRepository extends JpaRepository<PlatformRevenueEntry, Long> {

    Optional<PlatformRevenueEntry> findByOperationKey(String operationKey);

    long countByType(PlatformRevenueType type);

    @Query("select coalesce(sum(e.amount), 0) from PlatformRevenueEntry e where e.type = :type")
    BigDecimal sumAmountByType(@Param("type") PlatformRevenueType type);

    @Query(value = """
            SELECT count(*)
            FROM escrow_transactions e
            LEFT JOIN platform_revenue_entries p
              ON p.escrow_transaction_id = e.id
             AND p.type = 'PLATFORM_FEE'
            WHERE
                (e.status = 'RELEASED' AND (
                    (e.platform_fee_amount > 0 AND (
                        p.id IS NULL
                        OR p.amount <> e.platform_fee_amount
                        OR p.job_id <> e.job_id
                    ))
                    OR (e.platform_fee_amount = 0 AND p.id IS NOT NULL)
                ))
                OR (e.status <> 'RELEASED' AND p.id IS NOT NULL)
            """, nativeQuery = true)
    long countSettlementMismatches();
}
