package com.doFast.dofastapp.job.proposal;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface JobProposalRepository extends JpaRepository<JobProposal, Long> {
    Optional<JobProposal> findByJob_IdAndProposer_Id(Long jobId, Long proposerId);
    Optional<JobProposal> findByIdAndJob_Id(Long id, Long jobId);
    boolean existsByIdAndJob_IdAndProposer_Id(Long id, Long jobId, Long proposerId);
    List<JobProposal> findAllByJob_IdOrderByCreatedAtAscIdAsc(Long jobId);
    List<JobProposal> findAllByJob_IdAndStatusOrderByCreatedAtAscIdAsc(Long jobId, JobProposalStatus status);
}
