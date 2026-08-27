package com.doFast.dofastapp.job.search;

import com.doFast.dofastapp.common.exception.BusinessException;
import com.doFast.dofastapp.common.exception.ConflictException;
import com.doFast.dofastapp.common.exception.ResourceNotFoundException;
import com.doFast.dofastapp.job.category.JobCategory;
import com.doFast.dofastapp.job.category.JobCategoryRepository;
import com.doFast.dofastapp.user.entity.User;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;

@Service
@Transactional(readOnly = true)
public class SavedSearchService {

    static final int MAX_SAVED_SEARCHES_PER_USER = 20;

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
        if (query == null && categorySlug == null && minPrice == null && maxPrice == null) {
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
    }

    private SavedSearchResponse toResponse(SavedSearch savedSearch) {
        JobCategory category = savedSearch.getCategory();
        return new SavedSearchResponse(
                savedSearch.getId(),
                savedSearch.getName(),
                savedSearch.getQuery(),
                category != null ? category.getSlug() : null,
                category != null ? category.getName() : null,
                savedSearch.getMinPrice(),
                savedSearch.getMaxPrice(),
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
