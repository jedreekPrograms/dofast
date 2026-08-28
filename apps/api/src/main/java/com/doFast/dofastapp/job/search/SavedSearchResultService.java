package com.doFast.dofastapp.job.search;

import com.doFast.dofastapp.common.enums.JobStatus;
import com.doFast.dofastapp.common.exception.BusinessException;
import com.doFast.dofastapp.common.exception.ResourceNotFoundException;
import com.doFast.dofastapp.job.category.JobCategory;
import com.doFast.dofastapp.job.dto.NearbyJobResponse;
import com.doFast.dofastapp.job.repository.NearbyJobProjection;
import com.doFast.dofastapp.user.entity.User;
import org.locationtech.jts.geom.Point;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@Transactional(readOnly = true)
public class SavedSearchResultService {

    private final SavedSearchRepository savedSearchRepository;
    private final SavedSearchResultRepository savedSearchResultRepository;

    public SavedSearchResultService(
            SavedSearchRepository savedSearchRepository,
            SavedSearchResultRepository savedSearchResultRepository
    ) {
        this.savedSearchRepository = savedSearchRepository;
        this.savedSearchResultRepository = savedSearchResultRepository;
    }

    public List<NearbyJobResponse> getRadiusResults(Long id, User user, int limit) {
        SavedSearch savedSearch = savedSearchRepository.findByIdAndUser(id, user)
                .orElseThrow(() -> new ResourceNotFoundException("Zapisane wyszukiwanie nie istnieje"));

        Point center = savedSearch.getCenterLocation();
        Integer radiusMeters = savedSearch.getRadiusMeters();
        if (center == null || radiusMeters == null) {
            throw new BusinessException("To zapisane wyszukiwanie nie ma prywatnego filtra promienia");
        }
        if (user == null || user.getId() == null) {
            throw new ResourceNotFoundException("Zapisane wyszukiwanie nie istnieje");
        }

        JobCategory category = savedSearch.getCategory();
        return savedSearchResultRepository.findMatches(
                        center.getY(),
                        center.getX(),
                        radiusMeters,
                        savedSearch.getQuery(),
                        category != null ? category.getSlug() : null,
                        savedSearch.getMinPrice(),
                        savedSearch.getMaxPrice(),
                        user.getId(),
                        limit
                )
                .stream()
                .map(this::toResponse)
                .toList();
    }

    private NearbyJobResponse toResponse(NearbyJobProjection match) {
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
}
