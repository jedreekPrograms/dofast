package com.doFast.dofastapp.job.search;

import com.doFast.dofastapp.common.enums.JobStatus;
import com.doFast.dofastapp.common.exception.BusinessException;
import com.doFast.dofastapp.job.category.JobCategory;
import com.doFast.dofastapp.job.dto.NearbyJobResponse;
import com.doFast.dofastapp.job.repository.NearbyJobProjection;
import com.doFast.dofastapp.user.entity.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.PrecisionModel;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SavedSearchResultServiceTest {

    private static final GeometryFactory GEOMETRY_FACTORY = new GeometryFactory(new PrecisionModel(), 4326);

    @Mock private SavedSearchRepository savedSearchRepository;
    @Mock private SavedSearchResultRepository savedSearchResultRepository;
    @Mock private NearbyJobProjection projection;

    private SavedSearchResultService service;
    private User user;

    @BeforeEach
    void setUp() {
        service = new SavedSearchResultService(savedSearchRepository, savedSearchResultRepository);
        user = new User("user@example.com", "User");
        ReflectionTestUtils.setField(user, "id", 7L);
    }

    @Test
    void returnsOnlyDatabaseFilteredRadiusMatchesForCurrentViewerWithoutExposingCenter() {
        JobCategory category = new JobCategory();
        ReflectionTestUtils.setField(category, "slug", "przeprowadzki");

        SavedSearch savedSearch = new SavedSearch(user);
        ReflectionTestUtils.setField(savedSearch, "id", 9L);
        savedSearch.setQuery("kanapa");
        savedSearch.setCategory(category);
        savedSearch.setMinPrice(new BigDecimal("100.00"));
        savedSearch.setMaxPrice(new BigDecimal("500.00"));
        savedSearch.setCenterLocation(GEOMETRY_FACTORY.createPoint(new Coordinate(17.0385, 51.1079)));
        savedSearch.setRadiusMeters(15000);

        when(savedSearchRepository.findByIdAndUser(9L, user)).thenReturn(Optional.of(savedSearch));
        when(savedSearchResultRepository.findMatches(
                51.1079,
                17.0385,
                15000,
                "kanapa",
                "przeprowadzki",
                new BigDecimal("100.00"),
                new BigDecimal("500.00"),
                7L,
                50
        )).thenReturn(List.of(projection));

        when(projection.getId()).thenReturn(42L);
        when(projection.getTitle()).thenReturn("Przewóz kanapy");
        when(projection.getDescription()).thenReturn("Pomoc z transportem");
        when(projection.getPrice()).thenReturn(new BigDecimal("250.00"));
        when(projection.getStatus()).thenReturn("OPEN");
        when(projection.getLocationLabel()).thenReturn("Wrocław");
        when(projection.getDistanceMeters()).thenReturn(1234.4);
        when(projection.getCreatedAt()).thenReturn(LocalDateTime.of(2026, 8, 27, 6, 0));

        List<NearbyJobResponse> results = service.getRadiusResults(9L, user, 50);

        assertEquals(1, results.size());
        NearbyJobResponse result = results.getFirst();
        assertEquals(42L, result.id());
        assertEquals(JobStatus.OPEN, result.status());
        assertEquals(1234L, result.distanceMeters());
        verify(savedSearchResultRepository).findMatches(
                51.1079,
                17.0385,
                15000,
                "kanapa",
                "przeprowadzki",
                new BigDecimal("100.00"),
                new BigDecimal("500.00"),
                7L,
                50
        );
    }

    @Test
    void rejectsPresetWithoutPrivateRadius() {
        SavedSearch savedSearch = new SavedSearch(user);
        ReflectionTestUtils.setField(savedSearch, "id", 10L);
        savedSearch.setQuery("zakupy");
        when(savedSearchRepository.findByIdAndUser(10L, user)).thenReturn(Optional.of(savedSearch));

        assertThrows(BusinessException.class, () -> service.getRadiusResults(10L, user, 50));
        verify(savedSearchResultRepository, never()).findMatches(
                org.mockito.ArgumentMatchers.anyDouble(),
                org.mockito.ArgumentMatchers.anyDouble(),
                org.mockito.ArgumentMatchers.anyInt(),
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.anyLong(),
                org.mockito.ArgumentMatchers.anyInt()
        );
    }
}
