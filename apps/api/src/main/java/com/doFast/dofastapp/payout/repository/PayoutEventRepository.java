package com.doFast.dofastapp.payout.repository;

import com.doFast.dofastapp.payout.entity.PayoutEvent;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface PayoutEventRepository extends JpaRepository<PayoutEvent, Long> {

    @EntityGraph(attributePaths = "actor")
    List<PayoutEvent> findByPayout_IdOrderByCreatedAtAscIdAsc(Long payoutId);
}
