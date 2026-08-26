package com.doFast.dofastapp.job.service;

import com.doFast.dofastapp.common.enums.JobStatus;
import com.doFast.dofastapp.job.dto.NearbyJobResponse;
import com.doFast.dofastapp.job.repository.JobRepository;
import com.doFast.dofastapp.job.repository.NearbyJobProjection;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@Transactional(readOnly = true)
public class NearbyJobDiscoveryService {

    private final JobRepository jobRepository;

    public NearbyJobDiscoveryService(JobRepository jobRepository) {
        this.jobRepository = jobRepository;
    }

    public List<NearbyJobResponse> getNearbyJobs(
            double latitude,
            double longitude,
            int radiusMeters,
            String categorySlug,
            int limit
    ) {
        String normalizedCategory = normalizeCategorySlug(categorySlug);
        List<NearbyJobProjection> matches = normalizedCategory.isEmpty()
                ? jobRepository.findNearbyOpenJobs(latitude, longitude, radiusMeters, limit)
                : jobRepository.findNearbyOpenJobsByCategory(
                        latitude,
                        longitude,
                        radiusMeters,
                        normalizedCategory,
                        limit
                );
        return mapNearby(matches);
    }

    private List<NearbyJobResponse> mapNearby(List<NearbyJobProjection> matches) {
        return matches.stream()
                .map(match -> new NearbyJobResponse(
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
                ))
                .toList();
    }

    private String normalizeCategorySlug(String value) {
        if (value == null) return "";
        return value.trim().toLowerCase();
    }
}
