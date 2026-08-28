package com.doFast.dofastapp.payment.fee;

import org.springframework.data.jpa.repository.JpaRepository;

import java.math.BigDecimal;
import java.util.Optional;

public interface PlatformRevenueEntryRepository extends JpaRepository<PlatformRevenueEntry, Long> {

    Optional<PlatformRevenueEntry> findByOperationKey(String operationKey);

    long countByType(PlatformRevenueType type);

    @org.springframework.data.jpa.repository.Query("select coalesce(sum(e.amount), 0) from PlatformRevenueEntry e where e.type = :type")
    BigDecimal sumAmountByType(@org.springframework.data.repository.query.Param("type") PlatformRevenueType type);
}
