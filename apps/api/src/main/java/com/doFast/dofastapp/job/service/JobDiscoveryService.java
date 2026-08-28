package com.doFast.dofastapp.job.service;

import com.doFast.dofastapp.common.dto.PageResponse;
import com.doFast.dofastapp.common.enums.JobStatus;
import com.doFast.dofastapp.common.exception.BusinessException;
import com.doFast.dofastapp.job.dto.JobResponse;
import com.doFast.dofastapp.job.dto.NearbyJobResponse;
import com.doFast.dofastapp.job.entity.Job;
import com.doFast.dofastapp.job.repository.JobDiscoveryRepository;
import com.doFast.dofastapp.job.repository.NearbyJobProjection;
import com.doFast.dofastapp.user.entity.User;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;

@Service
@Transactional(readOnly = true)
public class JobDiscoveryService {

    private final JobDiscoveryRepository jobDiscoveryRepository;

    public JobDiscoveryService(JobDiscoveryRepository jobDiscoveryRepository) {
        this.jobDiscoveryRepository = jobDiscoveryRepository;
    }

    public PageResponse<JobResponse> getOpenJobs(
            String query,
            String categorySlug,
            BigDecimal minPrice,
            BigDecimal maxPrice,
            int page,
            int size,
            User viewer
    ) {
        if (minPrice != null && maxPrice != null && minPrice.compareTo(maxPrice) > 0) {
            throw new BusinessException("Minimalna cena nie może być większa od maksymalnej");
        }

        String normalizedQuery = normalizeSearchQuery(query);
        String normalizedCategory = normalizeCategorySlug(categorySlug);
        Long viewerId = viewerId(viewer);
        PageRequest pageable = PageRequest.of(
                page,
                size,
                Sort.by(Sort.Order.desc("createdAt"), Sort.Order.desc("id"))
        );

        Page<Job> result = normalizedCategory.isEmpty()
                ? jobDiscoveryRepository.findOpenJobs(
                        JobStatus.OPEN,
                        normalizedQuery,
                        minPrice,
                        maxPrice,
                        viewerId,
                        pageable
                )
                : jobDiscoveryRepository.findOpenJobsByCategory(
                        JobStatus.OPEN,
                        normalizedQuery,
                        normalizedCategory,
                        minPrice,
                        maxPrice,
                        viewerId,
                        pageable
                );

        return PageResponse.from(result, result.getContent().stream().map(JobResponseMapper::toResponse).toList());
    }

    public List<NearbyJobResponse> getNearbyJobs(
            double latitude,
            double longitude,
            int radiusMeters,
            String categorySlug,
            int limit,
            User viewer
    ) {
        String normalizedCategory = normalizeCategorySlug(categorySlug);
        Long viewerId = viewerId(viewer);
        List<NearbyJobProjection> matches = normalizedCategory.isEmpty()
                ? jobDiscoveryRepository.findNearbyOpenJobs(
                        latitude,
                        longitude,
                        radiusMeters,
                        viewerId,
                        limit
                )
                : jobDiscoveryRepository.findNearbyOpenJobsByCategory(
                        latitude,
                        longitude,
                        radiusMeters,
                        normalizedCategory,
                        viewerId,
                        limit
                );

        return matches.stream().map(this::toNearbyResponse).toList();
    }

    private NearbyJobResponse toNearbyResponse(NearbyJobProjection match) {
        return new NearbyJobResponse(
                match.getId(),
                match.getTitle(),
                match.getDescription(),
                match.getPrice(),
                JobStatus.valueOf(match.getStatus()),
                match.getLocationLabel(),
                match.getDestinationLabel(),
                match.getRouteDistanceMeters(),
                match.getRouteDurationSeconds(),
                Math.round(match.getDistanceMeters()),
                match.getCreatedAt()
        );
    }

    private Long viewerId(User viewer) {
        return viewer != null ? viewer.getId() : null;
    }

    private String normalizeSearchQuery(String value) {
        if (value == null) return "";
        return value.trim();
    }

    private String normalizeCategorySlug(String value) {
        if (value == null) return "";
        return value.trim().toLowerCase();
    }
}
