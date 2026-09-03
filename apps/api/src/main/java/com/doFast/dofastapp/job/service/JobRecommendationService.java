package com.doFast.dofastapp.job.service;

import com.doFast.dofastapp.common.dto.PageResponse;
import com.doFast.dofastapp.common.enums.JobStatus;
import com.doFast.dofastapp.common.exception.ForbiddenOperationException;
import com.doFast.dofastapp.job.dto.JobResponse;
import com.doFast.dofastapp.job.dto.RecommendedJobsResponse;
import com.doFast.dofastapp.job.entity.Job;
import com.doFast.dofastapp.job.repository.JobDiscoveryRepository;
import com.doFast.dofastapp.user.entity.User;
import com.doFast.dofastapp.user.entity.UserServiceArea;
import com.doFast.dofastapp.user.repository.UserServiceAreaRepository;
import com.doFast.dofastapp.user.repository.UserServiceCategoryRepository;
import org.locationtech.jts.geom.Point;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

@Service
@Transactional(readOnly = true)
public class JobRecommendationService {

    private final JobDiscoveryRepository jobDiscoveryRepository;
    private final UserServiceCategoryRepository userServiceCategoryRepository;
    private final UserServiceAreaRepository userServiceAreaRepository;

    public JobRecommendationService(
            JobDiscoveryRepository jobDiscoveryRepository,
            UserServiceCategoryRepository userServiceCategoryRepository,
            UserServiceAreaRepository userServiceAreaRepository
    ) {
        this.jobDiscoveryRepository = jobDiscoveryRepository;
        this.userServiceCategoryRepository = userServiceCategoryRepository;
        this.userServiceAreaRepository = userServiceAreaRepository;
    }

    public RecommendedJobsResponse getRecommendedJobs(User viewer, int page, int size) {
        Long viewerId = requireViewerId(viewer);
        List<Long> categoryIds = userServiceCategoryRepository.findActiveCategoryIdsForUser(viewerId);
        Optional<UserServiceArea> serviceArea = userServiceAreaRepository.findByUser_Id(viewerId);
        Integer serviceAreaRadiusKm = serviceArea.map(area -> area.getRadiusMeters() / 1000).orElse(null);
        PageRequest pageable = PageRequest.of(page, size);

        if (categoryIds.isEmpty()) {
            Page<Job> empty = Page.empty(pageable);
            return new RecommendedJobsResponse(
                    PageResponse.from(empty, List.of()),
                    0,
                    serviceAreaRadiusKm
            );
        }

        Page<Job> result = serviceArea
                .map(area -> findInServiceArea(categoryIds, viewerId, area, pageable))
                .orElseGet(() -> jobDiscoveryRepository.findRecommendedOpenJobs(
                        JobStatus.OPEN,
                        categoryIds,
                        viewerId,
                        pageable
                ));
        List<JobResponse> content = result.getContent().stream()
                .map(JobResponseMapper::toResponse)
                .toList();

        return new RecommendedJobsResponse(
                PageResponse.from(result, content),
                categoryIds.size(),
                serviceAreaRadiusKm
        );
    }

    private Long requireViewerId(User viewer) {
        if (viewer == null || viewer.getId() == null) {
            throw new ForbiddenOperationException("Zaloguj się, aby wyświetlić rekomendowane zlecenia");
        }
        return viewer.getId();
    }

    private Page<Job> findInServiceArea(
            List<Long> categoryIds,
            Long viewerId,
            UserServiceArea serviceArea,
            PageRequest pageable
    ) {
        Point center = serviceArea.getCenterLocation();
        return jobDiscoveryRepository.findRecommendedOpenJobsInArea(
                categoryIds,
                viewerId,
                center.getY(),
                center.getX(),
                serviceArea.getRadiusMeters(),
                pageable
        );
    }
}
