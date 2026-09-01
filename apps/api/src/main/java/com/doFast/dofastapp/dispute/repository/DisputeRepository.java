package com.doFast.dofastapp.dispute.repository;

import com.doFast.dofastapp.dispute.entity.Dispute;
import com.doFast.dofastapp.dispute.enums.DisputeStatus;
import com.doFast.dofastapp.job.entity.Job;
import com.doFast.dofastapp.user.entity.User;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface DisputeRepository extends JpaRepository<Dispute, Long> {

    Optional<Dispute> findFirstByJobAndStatusInOrderByOpenedAtDesc(
            Job job,
            Collection<DisputeStatus> statuses
    );

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select d from Dispute d where d.id = :id")
    Optional<Dispute> findByIdForUpdate(@Param("id") Long id);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select d from Dispute d
            where d.id = :id
              and d.openedBy.id = :userId
            """)
    Optional<Dispute> findByIdAndOpenedByIdForUpdate(
            @Param("id") Long id,
            @Param("userId") Long userId
    );

    @Query("""
            select d from Dispute d
            where d.job.createdBy = :user or d.job.takenBy = :user
            order by d.openedAt desc
            """)
    List<Dispute> findAllForParticipant(@Param("user") User user);

    Page<Dispute> findByStatus(DisputeStatus status, Pageable pageable);
}
