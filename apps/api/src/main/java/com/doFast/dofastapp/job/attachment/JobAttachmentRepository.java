package com.doFast.dofastapp.job.attachment;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface JobAttachmentRepository extends JpaRepository<JobAttachment, Long> {
    List<JobAttachment> findAllByJob_IdAndDeletedAtIsNullOrderByCreatedAtAscIdAsc(Long jobId);
    Optional<JobAttachment> findByIdAndJob_IdAndDeletedAtIsNull(Long id, Long jobId);
    long countByJob_IdAndDeletedAtIsNull(Long jobId);
}
