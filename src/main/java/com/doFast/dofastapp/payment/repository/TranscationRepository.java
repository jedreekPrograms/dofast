package com.doFast.dofastapp.payment.repository;

import com.doFast.dofastapp.job.entity.Job;
import com.doFast.dofastapp.payment.entity.Transaction;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface TranscationRepository extends JpaRepository<Transaction, Long> {

    Optional<Transaction> findByJob(Job job);
}
