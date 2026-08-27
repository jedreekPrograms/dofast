package com.doFast.dofastapp.job.search;

import com.doFast.dofastapp.common.exception.BusinessException;
import com.doFast.dofastapp.common.exception.ConflictException;
import com.doFast.dofastapp.common.exception.ResourceNotFoundException;
import com.doFast.dofastapp.job.category.JobCategory;
import com.doFast.dofastapp.job.category.JobCategoryRepository;
import com.doFast.dofastapp.user.entity.User;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.Point;
import org.locationtech.jts.geom.PrecisionModel;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;

@Service
@Transactional(readOnly = true)
public class SavedSearchService {

    static final int MAX_SAVED_SEARCHES_PER_USER = 20;
    private static final GeometryFactory GEOMETRY_FACTORY = new GeometryFactory(new PrecisionModel(), 4326);

    private final SavedSearchRepository savedSearchRepository;
    private final JobCategoryRepository jobCategoryRepository;

    public SavedSearchService(
            SavedSearchRepository savedSearchRepository,
            JobCategoryRepository jobCategoryRepository
    ) {
        this.savedSearchRepository = savedSearchRepository;
        this.jobCategoryRepository = jobCategoryRepository;
    }

    public List<SavedSearchResponse> list(User user) {
        return savedSearchRepository.findAllByUserOrderByUpdatedAtDescIdDesc(user)
                .stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional
    public SavedSearchResponse create(SavedSearchRequest request, User user) {
        if (savedSearchRepository.countByUser(user) >= MAX_SAVED_SEARCHES_PER_USER) {
            throw new ConflictException("Możesz mieć maksymalnie 20 zapisanych wyszukiwań");
        }

        String name = normalizeRequired(request.name());
        if (savedSearchRepository.existsByUserAndNameIgnoreCase(user, name)) {
            throw new ConflictException("Masz już zapisane wyszukiwanie o tej nazwie");
        }

        SavedSearch savedSearch = new SavedSearch(user);
        apply(savedSearch, request, name);
        return toResponse(savedSearchRepository.save(savedSearch));
    }

    @Transactional
    public SavedSearchResponse update(Long id, SavedSearchRequest request, User user) {
        SavedSearch savedSearch = savedSearchRepository.findByIdAndUser(id, user)
                .orElseThrow(() -> new ResourceNotFoundException("Zapisane wyszukiwanie nie istnieje"));

        String name = normalizeRequired(request.name());
        if (savedSearchRepository.existsByUserAndNameIgnoreCaseAndIdNot(user, name, id)) {
            throw new ConflictException("Masz już zapisane wyszukiwanie o tej nazwie");
        }

        apply(savedSearch, request, name);
        return toResponse(savedSearchRepository.save(savedSearch));
    }

    @Transactional
    public void delete(Long id, User user) {
        SavedSearch savedSearch = savedSearchRepository.findByIdAndUser(id, user)
                .orElseThrow(() -> new ResourceNotFoundException("Zapisane wyszukiwanie nie istnieje"));
        savedSearchRepository.delete(savedSearch);
    }

    private void apply(SavedSearch savedSearch, SavedSearchRequest request, String normalizedName) {
        String query = normalizeOptional(request.query());
        String categorySlug = normalizeOptional(request.categorySlug());
        BigDecimal minPrice = request.minPrice();
        BigDecimal maxPrice = request.maxPrice();

        if (minPrice != null && maxPrice != null && minPrice.compareTo(maxPrice) > 0) {
            throw new BusinessException("Minimalna cena nie może być większa od maksymalnej");
        }

        Point center = resolveCenter(request.latitude(), request.longitude(), request.radiusKm());
        Integer radiusMeters = request.radiusKm() == null ? null : request.radiusKm() * 1000;

        if (query == null && categorySlug == null && minPrice == null && maxPrice == null && center == null) {
            throw new BusinessException("Zapisane wyszukiwanie musi zawierać co najmniej jeden filtr");
        }

        JobCategory category = categorySlug == null
                ? null
                : jobCategoryRepository.findBySlugIgnoreCaseAndActiveTrue(categorySlug)
                        .orElseThrow(() -> new ResourceNotFoundException("Wybrana kategoria nie istnieje lub jest nieaktywna"));

        savedSearch.setName(normalizedName);
        savedSearch.setQuery(query);
        savedSearch.setCategory(category);
        savedSearch.setMinPrice(minPrice);
        savedSearch.setMaxPrice(maxPrice);
        savedSearch.setCenterLocation(center);
        savedSearch.setRadiusMeters(radiusMeters);
        savedSearch.setAlertsEnabled(request.alertsEnabled());
    }

    private Point resolveCenter(Double latitude, Double longitude, Integer radiusKm) {
        boolean any = latitude != null || longitude != null || radiusKm != null;
        boolean all = latitude != null && longitude != null && radiusKm != null;
        if (any && !all) {
            throw new BusinessException("Lokalizacja zapisanego wyszukiwania wymaga szerokości, długości i promienia");
        }
        if (!all) return null;
        if (!Double.isFinite(latitude) || latitude < -90 || latitude > 90) {
            throw new BusinessException("Szerokość geograficzna musi być w zakresie -90..90");
        }
        if (!Double.isFinite(longitude) || longitude < -180 || longitude > 180) {
            throw new BusinessException("Długość geograficzna musi być w zakresie -180..180");
        }
        if (radiusKm < 1 || radiusKm > 100) {
            throw new BusinessException("Promień musi być w zakresie 1..100 km");
        }
        return GEOMETRY_FACTORY.createPoint(new Coordinate(longitude, latitude));
    }

    private SavedSearchResponse toResponse(SavedSearch savedSearch) {
        JobCategory category = savedSearch.getCategory();
        Point center = savedSearch.getCenterLocation();
        return new SavedSearchResponse(
                savedSearch.getId(),
                savedSearch.getName(),
                savedSearch.getQuery(),
                category != null ? category.getSlug() : null,
                category != null ? category.getName() : null,
                savedSearch.getMinPrice(),
                savedSearch.getMaxPrice(),
                center != null ? center.getY() : null,
                center != null ? center.getX() : null,
                savedSearch.getRadiusMeters() != null ? savedSearch.getRadiusMeters() / 1000 : null,
                savedSearch.isAlertsEnabled(),
                savedSearch.getCreatedAt(),
                savedSearch.getUpdatedAt()
        );
    }

    private String normalizeRequired(String value) {
        return value.trim();
    }

    private String normalizeOptional(String value) {
        if (value == null) return null;
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
