package com.doFast.dofastapp.dispute.repository;

import com.doFast.dofastapp.dispute.entity.DisputeEvent;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface DisputeEventRepository extends JpaRepository<DisputeEvent, Long> {
    List<DisputeEvent> findByDispute_IdOrderByCreatedAtAsc(Long disputeId);
}
