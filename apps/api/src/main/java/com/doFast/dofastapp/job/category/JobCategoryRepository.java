package com.doFast.dofastapp.job.category;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface JobCategoryRepository extends JpaRepository<JobCategory, Long> {
    List<JobCategory> findByActiveTrueOrderBySortOrderAscNameAsc();
    List<JobCategory> findByIdInAndActiveTrue(Collection<Long> ids);
    Optional<JobCategory> findByIdAndActiveTrue(Long id);
    Optional<JobCategory> findBySlugIgnoreCaseAndActiveTrue(String slug);
}
