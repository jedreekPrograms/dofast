package com.doFast.dofastapp.job.search;

import com.doFast.dofastapp.common.exception.BusinessException;
import com.doFast.dofastapp.common.exception.ConflictException;
import com.doFast.dofastapp.job.category.JobCategory;
import com.doFast.dofastapp.job.category.JobCategoryRepository;
import com.doFast.dofastapp.user.entity.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SavedSearchServiceTest {

    @Mock private SavedSearchRepository savedSearchRepository;
    @Mock private JobCategoryRepository jobCategoryRepository;

    private SavedSearchService service;
    private User user;

    @BeforeEach
    void setUp() {
        service = new SavedSearchService(savedSearchRepository, jobCategoryRepository);
        user = new User("user@example.com", "User");
        ReflectionTestUtils.setField(user, "id", 7L);
    }

    @Test
    void createNormalizesPublicDiscoveryFiltersAndAlertPreference() {
        JobCategory category = new JobCategory();
        ReflectionTestUtils.setField(category, "id", 12L);
        ReflectionTestUtils.setField(category, "slug", "przeprowadzki");
        ReflectionTestUtils.setField(category, "name", "Przeprowadzki");
        ReflectionTestUtils.setField(category, "active", true);

        when(savedSearchRepository.countByUser(user)).thenReturn(2L);
        when(savedSearchRepository.existsByUserAndNameIgnoreCase(user, "Duże przeprowadzki")).thenReturn(false);
        when(jobCategoryRepository.findBySlugIgnoreCaseAndActiveTrue("przeprowadzki"))
                .thenReturn(Optional.of(category));
        when(savedSearchRepository.save(any(SavedSearch.class))).thenAnswer(invocation -> {
            SavedSearch saved = invocation.getArgument(0);
            ReflectionTestUtils.setField(saved, "id", 31L);
            ReflectionTestUtils.setField(saved, "createdAt", LocalDateTime.of(2026, 8, 27, 3, 0));
            ReflectionTestUtils.setField(saved, "updatedAt", LocalDateTime.of(2026, 8, 27, 3, 0));
            return saved;
        });

        SavedSearchResponse response = service.create(
                new SavedSearchRequest(
                        "  Duże przeprowadzki  ",
                        "  kanapa  ",
                        "  przeprowadzki  ",
                        new BigDecimal("100.00"),
                        new BigDecimal("500.00"),
                        null, null, null,
                        true
                ),
                user
        );

        ArgumentCaptor<SavedSearch> captor = ArgumentCaptor.forClass(SavedSearch.class);
        verify(savedSearchRepository).save(captor.capture());
        SavedSearch saved = captor.getValue();
        assertEquals("Duże przeprowadzki", saved.getName());
        assertEquals("kanapa", saved.getQuery());
        assertEquals(category, saved.getCategory());
        assertEquals(new BigDecimal("100.00"), saved.getMinPrice());
        assertTrue(saved.isAlertsEnabled());
        assertTrue(response.alertsEnabled());
        assertEquals("przeprowadzki", response.categorySlug());
    }

    @Test
    void createPersistsValidatedPrivateRadiusCriteria() {
        when(savedSearchRepository.countByUser(user)).thenReturn(0L);
        when(savedSearchRepository.existsByUserAndNameIgnoreCase(user, "Blisko mnie")).thenReturn(false);
        when(savedSearchRepository.save(any(SavedSearch.class))).thenAnswer(invocation -> {
            SavedSearch saved = invocation.getArgument(0);
            ReflectionTestUtils.setField(saved, "id", 32L);
            ReflectionTestUtils.setField(saved, "createdAt", LocalDateTime.of(2026, 8, 27, 4, 0));
            ReflectionTestUtils.setField(saved, "updatedAt", LocalDateTime.of(2026, 8, 27, 4, 0));
            return saved;
        });

        SavedSearchResponse response = service.create(
                new SavedSearchRequest("Blisko mnie", null, null, null, null, 51.1079, 17.0385, 15, true),
                user
        );

        ArgumentCaptor<SavedSearch> captor = ArgumentCaptor.forClass(SavedSearch.class);
        verify(savedSearchRepository).save(captor.capture());
        SavedSearch saved = captor.getValue();
        assertEquals(51.1079, saved.getCenterLocation().getY(), 0.000001);
        assertEquals(17.0385, saved.getCenterLocation().getX(), 0.000001);
        assertEquals(15000, saved.getRadiusMeters());
        assertEquals(15, response.radiusKm());
    }

    @Test
    void createRejectsPartialRadiusCriteria() {
        when(savedSearchRepository.countByUser(user)).thenReturn(0L);
        when(savedSearchRepository.existsByUserAndNameIgnoreCase(user, "Niepełna lokalizacja")).thenReturn(false);

        assertThrows(
                BusinessException.class,
                () -> service.create(
                        new SavedSearchRequest("Niepełna lokalizacja", null, null, null, null, 51.1, null, 10, true),
                        user
                )
        );
        verify(savedSearchRepository, never()).save(any());
    }

    @Test
    void createRejectsEmptyPreset() {
        when(savedSearchRepository.countByUser(user)).thenReturn(0L);
        when(savedSearchRepository.existsByUserAndNameIgnoreCase(user, "Wszystko")).thenReturn(false);

        assertThrows(
                BusinessException.class,
                () -> service.create(new SavedSearchRequest("Wszystko", " ", " ", null, null, null, null, null, false), user)
        );
        verify(savedSearchRepository, never()).save(any());
    }

    @Test
    void createRejectsInvalidPriceRange() {
        when(savedSearchRepository.countByUser(user)).thenReturn(0L);
        when(savedSearchRepository.existsByUserAndNameIgnoreCase(user, "Budżet")).thenReturn(false);

        assertThrows(
                BusinessException.class,
                () -> service.create(
                        new SavedSearchRequest(
                                "Budżet",
                                null,
                                null,
                                new BigDecimal("500.00"),
                                new BigDecimal("100.00"),
                                null, null, null,
                                false
                        ),
                        user
                )
        );
    }

    @Test
    void createRejectsDuplicateNameIgnoringCase() {
        when(savedSearchRepository.countByUser(user)).thenReturn(1L);
        when(savedSearchRepository.existsByUserAndNameIgnoreCase(user, "Paczki")).thenReturn(true);

        assertThrows(
                ConflictException.class,
                () -> service.create(new SavedSearchRequest("Paczki", "paczka", null, null, null, null, null, null, false), user)
        );
        verify(savedSearchRepository, never()).save(any());
    }

    @Test
    void createEnforcesPerUserLimit() {
        when(savedSearchRepository.countByUser(user)).thenReturn(20L);

        assertThrows(
                ConflictException.class,
                () -> service.create(new SavedSearchRequest("Jeszcze jedno", "zakupy", null, null, null, null, null, null, false), user)
        );
        verify(savedSearchRepository, never()).save(any());
    }

    @Test
    void updateCanClearOptionalCategoryAndPriceAndDisableAlert() {
        SavedSearch existing = new SavedSearch(user);
        existing.setAlertsEnabled(true);
        ReflectionTestUtils.setField(existing, "id", 9L);
        ReflectionTestUtils.setField(existing, "createdAt", LocalDateTime.of(2026, 8, 26, 22, 0));
        ReflectionTestUtils.setField(existing, "updatedAt", LocalDateTime.of(2026, 8, 26, 22, 0));
        when(savedSearchRepository.findByIdAndUser(9L, user)).thenReturn(Optional.of(existing));
        when(savedSearchRepository.existsByUserAndNameIgnoreCaseAndIdNot(user, "Zakupy", 9L)).thenReturn(false);
        when(savedSearchRepository.save(existing)).thenReturn(existing);

        service.update(9L, new SavedSearchRequest("Zakupy", "market", null, null, null, null, null, null, false), user);

        assertEquals("market", existing.getQuery());
        assertNull(existing.getCategory());
        assertNull(existing.getMinPrice());
        assertNull(existing.getMaxPrice());
        assertNull(existing.getCenterLocation());
        assertFalse(existing.isAlertsEnabled());
    }
}
