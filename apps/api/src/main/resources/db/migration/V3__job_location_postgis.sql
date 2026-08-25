CREATE EXTENSION IF NOT EXISTS postgis;

ALTER TABLE jobs
    ADD COLUMN location GEOGRAPHY(POINT, 4326),
    ADD COLUMN location_label VARCHAR(120),
    ADD COLUMN location_private_label VARCHAR(200);

CREATE INDEX idx_jobs_location_gist
    ON jobs
    USING GIST (location);

CREATE INDEX idx_jobs_open_location
    ON jobs (created_at DESC)
    WHERE status = 'OPEN' AND location IS NOT NULL;
