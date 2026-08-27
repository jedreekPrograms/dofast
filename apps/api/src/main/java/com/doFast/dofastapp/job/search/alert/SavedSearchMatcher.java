package com.doFast.dofastapp.job.search.alert;

import com.doFast.dofastapp.job.category.JobCategory;
import com.doFast.dofastapp.job.entity.Job;
import com.doFast.dofastapp.job.search.SavedSearch;
import org.springframework.stereotype.Component;

import java.util.Locale;

@Component
public class SavedSearchMatcher {

    public boolean matches(SavedSearch savedSearch, Job job) {
        if (!savedSearch.isAlertsEnabled()) return false;
        if (savedSearch.getUser().getId().equals(job.getCreatedBy().getId())) return false;

        if (savedSearch.getMinPrice() != null && job.getPrice().compareTo(savedSearch.getMinPrice()) < 0) return false;
        if (savedSearch.getMaxPrice() != null && job.getPrice().compareTo(savedSearch.getMaxPrice()) > 0) return false;

        if (savedSearch.getQuery() != null) {
            String haystack = (job.getTitle() + " " + job.getDescription()).toLowerCase(Locale.ROOT);
            if (!haystack.contains(savedSearch.getQuery().toLowerCase(Locale.ROOT))) return false;
        }

        JobCategory wanted = savedSearch.getCategory();
        if (wanted != null) {
            JobCategory actual = job.getCategory();
            if (actual == null) return false;
            boolean sameCategory = wanted.getId().equals(actual.getId());
            boolean parentCategory = actual.getParent() != null && wanted.getId().equals(actual.getParent().getId());
            if (!sameCategory && !parentCategory) return false;
        }

        return true;
    }
}
