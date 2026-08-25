package com.doFast.dofastapp.verification.repository;

import com.doFast.dofastapp.verification.entity.VerificationEvent;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface VerificationEventRepository extends JpaRepository<VerificationEvent, Long> {

    @EntityGraph(attributePaths = {"actor"})
    List<VerificationEvent> findByVerification_IdOrderByCreatedAtAscIdAsc(Long verificationId);
}
