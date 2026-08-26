package com.doFast.dofastapp.job.category;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface JobCategoryRepository extends JpaRepository<JobCategory, Long> {
    List<JobCategory> findByActiveTrueOrderBySortOrderAscNameAsc();
    Optional<JobCategory> findByIdAndActiveTrue(Long id);
}
