package com.doFast.dofastapp.job.search.alert;

import com.doFast.dofastapp.common.enums.JobStatus;
import com.doFast.dofastapp.job.category.JobCategory;
import com.doFast.dofastapp.job.entity.Job;
import com.doFast.dofastapp.job.search.SavedSearch;
import org.locationtech.jts.geom.Point;
import org.springframework.stereotype.Component;

import java.util.Locale;

@Component
public class SavedSearchMatcher {

    private static final double EARTH_RADIUS_METERS = 6_371_000.0;

    public boolean matches(SavedSearch savedSearch, Job job) {
        if (!savedSearch.isAlertsEnabled()) return false;
        if (job.getStatus() != JobStatus.OPEN) return false;
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

        if (savedSearch.getCenterLocation() != null) {
            Point jobLocation = job.getLocation();
            if (jobLocation == null || savedSearch.getRadiusMeters() == null) return false;
            if (distanceMeters(savedSearch.getCenterLocation(), jobLocation) > savedSearch.getRadiusMeters()) return false;
        }

        return true;
    }

    private double distanceMeters(Point a, Point b) {
        double lat1 = Math.toRadians(a.getY());
        double lat2 = Math.toRadians(b.getY());
        double deltaLat = lat2 - lat1;
        double deltaLon = Math.toRadians(b.getX() - a.getX());
        double sinLat = Math.sin(deltaLat / 2.0);
        double sinLon = Math.sin(deltaLon / 2.0);
        double haversine = sinLat * sinLat + Math.cos(lat1) * Math.cos(lat2) * sinLon * sinLon;
        return 2.0 * EARTH_RADIUS_METERS * Math.asin(Math.min(1.0, Math.sqrt(haversine)));
    }
}
