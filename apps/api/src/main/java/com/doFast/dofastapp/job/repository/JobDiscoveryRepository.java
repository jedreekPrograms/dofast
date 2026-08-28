package com.doFast.dofastapp.job.repository;

import com.doFast.dofastapp.common.enums.JobStatus;
import com.doFast.dofastapp.job.entity.Job;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.util.List;

public interface JobDiscoveryRepository extends Repository<Job, Long> {

    @Query("""
            select j
            from Job j
            where j.status = :status
              and (
                    :query = ''
                    or lower(j.title) like lower(concat('%', :query, '%'))
                    or lower(j.description) like lower(concat('%', :query, '%'))
                    or lower(j.locationLabel) like lower(concat('%', :query, '%'))
                    or lower(j.destinationLabel) like lower(concat('%', :query, '%'))
              )
              and (:minPrice is null or j.price >= :minPrice)
              and (:maxPrice is null or j.price <= :maxPrice)
              and (
                    :viewerId is null
                    or not exists (
                        select ub.id
                        from UserBlock ub
                        where (ub.blocker.id = :viewerId and ub.blockedUser.id = j.createdBy.id)
                           or (ub.blocker.id = j.createdBy.id and ub.blockedUser.id = :viewerId)
                    )
              )
            """)
    Page<Job> findOpenJobs(
            @Param("status") JobStatus status,
            @Param("query") String query,
            @Param("minPrice") BigDecimal minPrice,
            @Param("maxPrice") BigDecimal maxPrice,
            @Param("viewerId") Long viewerId,
            Pageable pageable
    );

    @Query("""
            select j
            from Job j
            join j.category category
            left join category.parent parentCategory
            where j.status = :status
              and (
                    :query = ''
                    or lower(j.title) like lower(concat('%', :query, '%'))
                    or lower(j.description) like lower(concat('%', :query, '%'))
                    or lower(j.locationLabel) like lower(concat('%', :query, '%'))
                    or lower(j.destinationLabel) like lower(concat('%', :query, '%'))
              )
              and (
                    lower(category.slug) = lower(:categorySlug)
                    or lower(parentCategory.slug) = lower(:categorySlug)
              )
              and (:minPrice is null or j.price >= :minPrice)
              and (:maxPrice is null or j.price <= :maxPrice)
              and (
                    :viewerId is null
                    or not exists (
                        select ub.id
                        from UserBlock ub
                        where (ub.blocker.id = :viewerId and ub.blockedUser.id = j.createdBy.id)
                           or (ub.blocker.id = j.createdBy.id and ub.blockedUser.id = :viewerId)
                    )
              )
            """)
    Page<Job> findOpenJobsByCategory(
            @Param("status") JobStatus status,
            @Param("query") String query,
            @Param("categorySlug") String categorySlug,
            @Param("minPrice") BigDecimal minPrice,
            @Param("maxPrice") BigDecimal maxPrice,
            @Param("viewerId") Long viewerId,
            Pageable pageable
    );

    @Query("""
            select j
            from Job j
            join j.category category
            where j.status = :status
              and category.id in :categoryIds
              and j.createdBy.id <> :viewerId
              and not exists (
                    select ub.id
                    from UserBlock ub
                    where (ub.blocker.id = :viewerId and ub.blockedUser.id = j.createdBy.id)
                       or (ub.blocker.id = j.createdBy.id and ub.blockedUser.id = :viewerId)
              )
            """)
    Page<Job> findRecommendedOpenJobs(
            @Param("status") JobStatus status,
            @Param("categoryIds") List<Long> categoryIds,
            @Param("viewerId") Long viewerId,
            Pageable pageable
    );

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
            CROSS JOIN origin
            WHERE j.status = 'OPEN'
              AND j.location IS NOT NULL
              AND ST_DWithin(j.location, origin.point, :radiusMeters)
              AND (
                    :viewerId IS NULL
                    OR NOT EXISTS (
                        SELECT 1
                        FROM user_blocks ub
                        WHERE (ub.blocker_id = :viewerId AND ub.blocked_user_id = j.created_by_id)
                           OR (ub.blocker_id = j.created_by_id AND ub.blocked_user_id = :viewerId)
                    )
              )
            ORDER BY j.location <-> origin.point, j.created_at DESC
            LIMIT :limit
            """, nativeQuery = true)
    List<NearbyJobProjection> findNearbyOpenJobs(
            @Param("latitude") double latitude,
            @Param("longitude") double longitude,
            @Param("radiusMeters") int radiusMeters,
            @Param("viewerId") Long viewerId,
            @Param("limit") int limit
    );

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
              AND (lower(category.slug) = lower(:categorySlug) OR lower(parent_category.slug) = lower(:categorySlug))
              AND ST_DWithin(j.location, origin.point, :radiusMeters)
              AND (
                    :viewerId IS NULL
                    OR NOT EXISTS (
                        SELECT 1
                        FROM user_blocks ub
                        WHERE (ub.blocker_id = :viewerId AND ub.blocked_user_id = j.created_by_id)
                           OR (ub.blocker_id = j.created_by_id AND ub.blocked_user_id = :viewerId)
                    )
              )
            ORDER BY j.location <-> origin.point, j.created_at DESC
            LIMIT :limit
            """, nativeQuery = true)
    List<NearbyJobProjection> findNearbyOpenJobsByCategory(
            @Param("latitude") double latitude,
            @Param("longitude") double longitude,
            @Param("radiusMeters") int radiusMeters,
            @Param("categorySlug") String categorySlug,
            @Param("viewerId") Long viewerId,
            @Param("limit") int limit
    );
}
