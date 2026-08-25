package com.doFast.dofastapp.job.repository;

import com.doFast.dofastapp.common.enums.JobStatus;
import com.doFast.dofastapp.job.entity.Job;
import com.doFast.dofastapp.user.entity.User;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface JobRepository extends JpaRepository<Job, Long> {

    List<Job> findByStatusOrderByCreatedAtDesc(JobStatus status);

    List<Job> findByCreatedByOrTakenByOrderByCreatedAtDesc(User createdBy, User takenBy);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select j from Job j where j.id = :id")
    Optional<Job> findByIdForUpdate(@Param("id") Long id);

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
                ST_Distance(j.location, origin.point) AS "distanceMeters",
                j.created_at AS "createdAt"
            FROM jobs j
            CROSS JOIN origin
            WHERE j.status = 'OPEN'
              AND j.location IS NOT NULL
              AND ST_DWithin(j.location, origin.point, :radiusMeters)
            ORDER BY j.location <-> origin.point, j.created_at DESC
            LIMIT :limit
            """, nativeQuery = true)
    List<NearbyJobProjection> findNearbyOpenJobs(
            @Param("latitude") double latitude,
            @Param("longitude") double longitude,
            @Param("radiusMeters") int radiusMeters,
            @Param("limit") int limit
    );
}
