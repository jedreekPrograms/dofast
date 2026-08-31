package com.doFast.dofastapp.location.retention;

import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Repository
public class ExactLocationRetentionRepository {

    private final NamedParameterJdbcTemplate jdbc;

    public ExactLocationRetentionRepository(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public int purgeDueJobs(LocalDateTime cutoff, int batchSize) {
        MapSqlParameterSource parameters = new MapSqlParameterSource()
                .addValue("cutoff", cutoff)
                .addValue("batchSize", batchSize);

        List<RetentionCandidate> candidates = jdbc.query("""
                SELECT id, route_quote_id
                FROM jobs
                WHERE exact_location_purged_at IS NULL
                  AND (
                        (status = 'DONE' AND completed_at IS NOT NULL AND completed_at < :cutoff)
                        OR
                        (status = 'CANCELLED' AND cancelled_at IS NOT NULL AND cancelled_at < :cutoff)
                  )
                ORDER BY COALESCE(completed_at, cancelled_at), id
                LIMIT :batchSize
                FOR UPDATE SKIP LOCKED
                """, parameters, (rs, rowNum) -> new RetentionCandidate(
                rs.getLong("id"),
                rs.getObject("route_quote_id", UUID.class)
        ));

        if (candidates.isEmpty()) {
            return 0;
        }

        List<Long> jobIds = candidates.stream().map(RetentionCandidate::jobId).toList();
        List<UUID> routeQuoteIds = candidates.stream()
                .map(RetentionCandidate::routeQuoteId)
                .filter(id -> id != null)
                .toList();

        MapSqlParameterSource jobParameters = new MapSqlParameterSource("jobIds", jobIds);

        jdbc.update("""
                UPDATE job_route_stops
                SET location = NULL,
                    private_label = NULL,
                    place_id = NULL
                WHERE job_id IN (:jobIds)
                """, jobParameters);

        jdbc.update("""
                UPDATE jobs
                SET location = NULL,
                    location_private_label = NULL,
                    destination_location = NULL,
                    destination_private_label = NULL,
                    route_encoded_polyline = NULL,
                    route_quote_id = NULL,
                    exact_location_purged_at = CURRENT_TIMESTAMP
                WHERE id IN (:jobIds)
                  AND exact_location_purged_at IS NULL
                """, jobParameters);

        if (!routeQuoteIds.isEmpty()) {
            jdbc.update("""
                    DELETE FROM route_quotes quote
                    WHERE quote.id IN (:routeQuoteIds)
                      AND NOT EXISTS (
                            SELECT 1
                            FROM jobs job
                            WHERE job.route_quote_id = quote.id
                      )
                    """, new MapSqlParameterSource("routeQuoteIds", routeQuoteIds));
        }

        return candidates.size();
    }

    public int deleteExpiredUnreferencedRouteQuotes(LocalDateTime now, int batchSize) {
        MapSqlParameterSource parameters = new MapSqlParameterSource()
                .addValue("now", now)
                .addValue("batchSize", batchSize);

        return jdbc.update("""
                DELETE FROM route_quotes quote
                WHERE quote.id IN (
                    SELECT candidate.id
                    FROM (
                        SELECT route_quote.id
                        FROM route_quotes route_quote
                        WHERE route_quote.expires_at < :now
                          AND NOT EXISTS (
                                SELECT 1
                                FROM jobs job
                                WHERE job.route_quote_id = route_quote.id
                          )
                        ORDER BY route_quote.expires_at, route_quote.id
                        LIMIT :batchSize
                        FOR UPDATE SKIP LOCKED
                    ) candidate
                )
                """, parameters);
    }

    record RetentionCandidate(Long jobId, UUID routeQuoteId) {}
}
