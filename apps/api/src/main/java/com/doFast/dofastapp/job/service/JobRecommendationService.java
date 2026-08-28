package com.doFast.dofastapp.job.service;

import com.doFast.dofastapp.common.dto.PageResponse;
import com.doFast.dofastapp.common.enums.JobStatus;
import com.doFast.dofastapp.job.dto.JobResponse;
import com.doFast.dofastapp.job.dto.RecommendedJobsResponse;
import com.doFast.dofastapp.job.entity.Job;
import com.doFast.dofastapp.job.repository.JobDiscoveryRepository;
import com.doFast.dofastapp.user.entity.User;
import com.doFast.dofastapp.user.repository.UserServiceCategoryRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@Transactional(readOnly = true)
public class JobRecommendationService {

    private final JobDiscoveryRepository jobDiscoveryRepository;
    private final UserServiceCategoryRepository userServiceCategoryRepository;

    public JobRecommendationService(
            JobDiscoveryRepository jobDiscoveryRepository,
            UserServiceCategoryRepository userServiceCategoryRepository
    ) {
        this.jobDiscoveryRepository = jobDiscoveryRepository;
        this.userServiceCategoryRepository = userServiceCategoryRepository;
    }

    public RecommendedJobsResponse getRecommendedJobs(User viewer, int page, int size) {
        Long viewerId = viewer.getId();
        List<Long> categoryIds = userServiceCategoryRepository.findActiveCategoryIdsForUser(viewerId);
        PageRequest pageable = PageRequest.of(
                page,
                size,
                Sort.by(Sort.Order.desc("createdAt"), Sort.Order.desc("id"))
        );

        if (categoryIds.isEmpty()) {
            Page<Job> empty = Page.empty(pageable);
            return new RecommendedJobsResponse(
                    PageResponse.from(empty, List.of()),
                    0
            );
        }

        Page<Job> result = jobDiscoveryRepository.findRecommendedOpenJobs(
                JobStatus.OPEN,
                categoryIds,
                viewerId,
                pageable
        );
        List<JobResponse> content = result.getContent().stream()
                .map(JobResponseMapper::toResponse)
                .toList();

        return new RecommendedJobsResponse(
                PageResponse.from(result, content),
                categoryIds.size()
        );
    }
}
