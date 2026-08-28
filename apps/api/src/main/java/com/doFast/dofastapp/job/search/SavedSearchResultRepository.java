package com.doFast.dofastapp.job.search;

import com.doFast.dofastapp.job.entity.Job;
import com.doFast.dofastapp.job.repository.NearbyJobProjection;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.util.List;

interface SavedSearchResultRepository extends Repository<Job, Long> {

    @Query(value = """
            WITH origin AS (
                SELECT CAST(ST_SetSRID(ST_MakePoint(:longitude, :latitude), 4326) AS geography) AS point
            )
            SELECT
                j.id AS "id",
                j.title AS "title",
                j.description AS "description",
                j.price AS "price",
                j.status AS "status",
                j.location_label AS "locationLabel",
                j.destination_label AS "destinationLabel",
                j.route_distance_meters AS "routeDistanceMeters",
                j.route_duration_seconds AS "routeDurationSeconds",
                ST_Distance(j.location, origin.point) AS "distanceMeters",
                j.created_at AS "createdAt"
            FROM jobs j
            JOIN job_categories category ON category.id = j.category_id
            LEFT JOIN job_categories parent_category ON parent_category.id = category.parent_id
            CROSS JOIN origin
            WHERE j.status = 'OPEN'
              AND j.location IS NOT NULL
              AND ST_DWithin(j.location, origin.point, :radiusMeters)
              AND (
                    :categorySlug IS NULL
                    OR lower(category.slug) = lower(:categorySlug)
                    OR lower(parent_category.slug) = lower(:categorySlug)
              )
              AND (
                    :query IS NULL
                    OR lower(j.title) LIKE lower(concat('%', :query, '%'))
                    OR lower(j.description) LIKE lower(concat('%', :query, '%'))
                    OR lower(j.location_label) LIKE lower(concat('%', :query, '%'))
                    OR lower(j.destination_label) LIKE lower(concat('%', :query, '%'))
              )
              AND (:minPrice IS NULL OR j.price >= :minPrice)
              AND (:maxPrice IS NULL OR j.price <= :maxPrice)
              AND NOT EXISTS (
                    SELECT 1
                    FROM user_blocks ub
                    WHERE (ub.blocker_id = :viewerId AND ub.blocked_user_id = j.created_by_id)
                       OR (ub.blocker_id = j.created_by_id AND ub.blocked_user_id = :viewerId)
              )
            ORDER BY j.location <-> origin.point, j.created_at DESC
            LIMIT :limit
            """, nativeQuery = true)
    List<NearbyJobProjection> findMatches(
            @Param("latitude") double latitude,
            @Param("longitude") double longitude,
            @Param("radiusMeters") int radiusMeters,
            @Param("query") String query,
            @Param("categorySlug") String categorySlug,
            @Param("minPrice") BigDecimal minPrice,
            @Param("maxPrice") BigDecimal maxPrice,
            @Param("viewerId") Long viewerId,
            @Param("limit") int limit
    );
}
