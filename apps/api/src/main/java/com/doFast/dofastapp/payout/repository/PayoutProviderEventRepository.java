package com.doFast.dofastapp.payout.repository;

import com.doFast.dofastapp.payout.entity.PayoutProviderEvent;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PayoutProviderEventRepository extends JpaRepository<PayoutProviderEvent, Long> {
    boolean existsByProviderCodeAndProviderEventId(String providerCode, String providerEventId);
}
