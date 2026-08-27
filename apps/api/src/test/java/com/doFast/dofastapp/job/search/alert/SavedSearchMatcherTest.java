package com.doFast.dofastapp.job.search.alert;

import com.doFast.dofastapp.job.category.JobCategory;
import com.doFast.dofastapp.job.entity.Job;
import com.doFast.dofastapp.job.search.SavedSearch;
import com.doFast.dofastapp.user.entity.User;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SavedSearchMatcherTest {

    private final SavedSearchMatcher matcher = new SavedSearchMatcher();

    @Test
    void matchesQueryParentCategoryAndPriceRange() {
        User owner = user(1L);
        User subscriber = user(2L);
        JobCategory parent = category(10L, null);
        JobCategory leaf = category(11L, parent);

        Job job = job(owner, leaf, "Przewóz dużej kanapy", "Pomoc przy przeprowadzce", "250.00");
        SavedSearch savedSearch = savedSearch(subscriber, parent, "kanapa", "200.00", "300.00", true);

        assertTrue(matcher.matches(savedSearch, job));
    }

    @Test
    void neverAlertsJobOwnerOrDisabledPreset() {
        User owner = user(1L);
        JobCategory leaf = category(11L, category(10L, null));
        Job job = job(owner, leaf, "Zakupy", "Odbiór ze sklepu", "40.00");

        assertFalse(matcher.matches(savedSearch(owner, null, "zakupy", null, null, true), job));
        assertFalse(matcher.matches(savedSearch(user(2L), null, "zakupy", null, null, false), job));
    }

    @Test
    void rejectsPriceAndCategoryMismatch() {
        User owner = user(1L);
        User subscriber = user(2L);
        JobCategory transport = category(10L, null);
        JobCategory parcel = category(11L, transport);
        JobCategory repairs = category(20L, null);
        Job job = job(owner, parcel, "Paczka", "Mała przesyłka", "120.00");

        assertFalse(matcher.matches(savedSearch(subscriber, repairs, null, null, null, true), job));
        assertFalse(matcher.matches(savedSearch(subscriber, null, null, "150.00", null, true), job));
    }

    private SavedSearch savedSearch(
            User user,
            JobCategory category,
            String query,
            String minPrice,
            String maxPrice,
            boolean enabled
    ) {
        SavedSearch savedSearch = new SavedSearch(user);
        savedSearch.setCategory(category);
        savedSearch.setQuery(query);
        savedSearch.setMinPrice(minPrice == null ? null : new BigDecimal(minPrice));
        savedSearch.setMaxPrice(maxPrice == null ? null : new BigDecimal(maxPrice));
        savedSearch.setAlertsEnabled(enabled);
        return savedSearch;
    }

    private Job job(User owner, JobCategory category, String title, String description, String price) {
        Job job = new Job();
        ReflectionTestUtils.setField(job, "id", 50L);
        job.setCreatedBy(owner);
        job.setCategory(category);
        job.setTitle(title);
        job.setDescription(description);
        job.setPrice(new BigDecimal(price));
        return job;
    }

    private User user(Long id) {
        User user = new User("u" + id + "@example.com", "U" + id);
        ReflectionTestUtils.setField(user, "id", id);
        return user;
    }

    private JobCategory category(Long id, JobCategory parent) {
        JobCategory category = new JobCategory();
        ReflectionTestUtils.setField(category, "id", id);
        ReflectionTestUtils.setField(category, "parent", parent);
        return category;
    }
}
