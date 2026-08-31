ALTER TABLE jobs
    ADD COLUMN exact_location_purged_at TIMESTAMP;

ALTER TABLE job_route_stops
    ALTER COLUMN location DROP NOT NULL;

ALTER TABLE jobs
    ADD CONSTRAINT chk_jobs_exact_location_purge_terminal
        CHECK (exact_location_purged_at IS NULL OR status IN ('DONE', 'CANCELLED'));

CREATE INDEX idx_jobs_exact_location_retention_due
    ON jobs (COALESCE(completed_at, cancelled_at), id)
    WHERE exact_location_purged_at IS NULL
      AND status IN ('DONE', 'CANCELLED');
