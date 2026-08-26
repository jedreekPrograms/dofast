package com.doFast.dofastapp.job.saved;

import com.doFast.dofastapp.common.enums.JobStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;

public interface SavedJobRepository extends JpaRepository<SavedJob, Long> {

    boolean existsByUser_IdAndJob_Id(Long userId, Long jobId);

    @Modifying
    long deleteByUser_IdAndJob_Id(Long userId, Long jobId);

    @Query("""
            select saved.job.id
            from SavedJob saved
            where saved.user.id = :userId
              and saved.job.id in :jobIds
            """)
    List<Long> findSavedJobIds(
            @Param("userId") Long userId,
            @Param("jobIds") Collection<Long> jobIds
    );

    @Query(
            value = """
                    select saved
                    from SavedJob saved
                    join fetch saved.job job
                    where saved.user.id = :userId
                      and job.status = :status
                    order by saved.createdAt desc
                    """,
            countQuery = """
                    select count(saved)
                    from SavedJob saved
                    join saved.job job
                    where saved.user.id = :userId
                      and job.status = :status
                    """
    )
    Page<SavedJob> findByUserAndJobStatus(
            @Param("userId") Long userId,
            @Param("status") JobStatus status,
            Pageable pageable
    );
}
