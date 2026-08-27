package com.doFast.dofastapp.job.search.alert;

import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

import java.util.List;

public interface JobPublicationOutboxRepository extends JpaRepository<JobPublicationOutbox, Long> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select e from JobPublicationOutbox e join fetch e.job j join fetch j.createdBy left join fetch j.category c left join fetch c.parent where e.processedAt is null order by e.id asc")
    List<JobPublicationOutbox> findPendingForUpdate(Pageable pageable);
}
