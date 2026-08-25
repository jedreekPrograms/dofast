CREATE TABLE job_live_tracking (
    job_id BIGINT PRIMARY KEY REFERENCES jobs(id) ON DELETE CASCADE,
    worker_id BIGINT NOT NULL REFERENCES users(id),
    version INTEGER NOT NULL DEFAULT 0,
    phase VARCHAR(32) NOT NULL,
    current_location geography(Point,4326),
    accuracy_meters DOUBLE PRECISION,
    heading_degrees DOUBLE PRECISION,
    speed_meters_per_second DOUBLE PRECISION,
    captured_at TIMESTAMP WITH TIME ZONE,
    received_at TIMESTAMP WITH TIME ZONE,
    sharing_started_at TIMESTAMP WITH TIME ZONE NOT NULL,
    sharing_stopped_at TIMESTAMP WITH TIME ZONE,
    remaining_distance_meters INTEGER,
    remaining_duration_seconds INTEGER,
    remaining_encoded_polyline TEXT,
    remaining_provider VARCHAR(32),
    remaining_computed_at TIMESTAMP WITH TIME ZONE,
    eta_origin_location geography(Point,4326),
    CONSTRAINT chk_live_tracking_phase CHECK (phase IN ('TO_ORIGIN', 'TO_DESTINATION')),
    CONSTRAINT chk_live_tracking_accuracy CHECK (accuracy_meters IS NULL OR accuracy_meters >= 0),
    CONSTRAINT chk_live_tracking_heading CHECK (heading_degrees IS NULL OR (heading_degrees >= 0 AND heading_degrees <= 360)),
    CONSTRAINT chk_live_tracking_speed CHECK (speed_meters_per_second IS NULL OR speed_meters_per_second >= 0),
    CONSTRAINT chk_live_tracking_distance CHECK (remaining_distance_meters IS NULL OR remaining_distance_meters >= 0),
    CONSTRAINT chk_live_tracking_duration CHECK (remaining_duration_seconds IS NULL OR remaining_duration_seconds >= 0)
);

CREATE INDEX idx_job_live_tracking_worker ON job_live_tracking(worker_id);
CREATE INDEX idx_job_live_tracking_current_location
    ON job_live_tracking USING GIST(current_location);

CREATE OR REPLACE FUNCTION clear_live_tracking_when_job_pauses_or_closes()
RETURNS TRIGGER AS $$
BEGIN
    IF NEW.status IN ('DISPUTED', 'DONE', 'CANCELLED')
       AND OLD.status IS DISTINCT FROM NEW.status THEN
        UPDATE job_live_tracking
        SET current_location = NULL,
            accuracy_meters = NULL,
            heading_degrees = NULL,
            speed_meters_per_second = NULL,
            captured_at = NULL,
            received_at = CURRENT_TIMESTAMP,
            sharing_stopped_at = CURRENT_TIMESTAMP,
            remaining_distance_meters = NULL,
            remaining_duration_seconds = NULL,
            remaining_encoded_polyline = NULL,
            remaining_provider = NULL,
            remaining_computed_at = NULL,
            eta_origin_location = NULL,
            version = version + 1
        WHERE job_id = NEW.id;
    END IF;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_jobs_clear_live_tracking
AFTER UPDATE OF status ON jobs
FOR EACH ROW
EXECUTE FUNCTION clear_live_tracking_when_job_pauses_or_closes();
