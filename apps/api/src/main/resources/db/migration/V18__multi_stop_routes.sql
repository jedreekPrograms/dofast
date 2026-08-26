CREATE TABLE route_quote_stops (
    id BIGSERIAL PRIMARY KEY,
    route_quote_id UUID NOT NULL REFERENCES route_quotes(id) ON DELETE CASCADE,
    sequence_no INTEGER NOT NULL CHECK (sequence_no BETWEEN 0 AND 9),
    location GEOGRAPHY(Point, 4326) NOT NULL,
    public_label VARCHAR(120) NOT NULL,
    private_label VARCHAR(200),
    place_id VARCHAR(255),
    CONSTRAINT uk_route_quote_stops_sequence UNIQUE (route_quote_id, sequence_no)
);

CREATE INDEX idx_route_quote_stops_quote_sequence
    ON route_quote_stops(route_quote_id, sequence_no);

CREATE TABLE job_route_stops (
    id BIGSERIAL PRIMARY KEY,
    job_id BIGINT NOT NULL REFERENCES jobs(id) ON DELETE CASCADE,
    sequence_no INTEGER NOT NULL CHECK (sequence_no BETWEEN 0 AND 9),
    location GEOGRAPHY(Point, 4326) NOT NULL,
    public_label VARCHAR(120) NOT NULL,
    private_label VARCHAR(200),
    place_id VARCHAR(255),
    CONSTRAINT uk_job_route_stops_sequence UNIQUE (job_id, sequence_no)
);

CREATE INDEX idx_job_route_stops_job_sequence
    ON job_route_stops(job_id, sequence_no);

ALTER TABLE job_live_tracking
    DROP CONSTRAINT chk_live_tracking_phase,
    ADD COLUMN next_stop_sequence INTEGER,
    ADD CONSTRAINT chk_live_tracking_phase
        CHECK (phase IN ('TO_ORIGIN', 'TO_STOP', 'TO_DESTINATION')),
    ADD CONSTRAINT chk_live_tracking_stop_sequence_range
        CHECK (next_stop_sequence IS NULL OR next_stop_sequence BETWEEN 0 AND 9),
    ADD CONSTRAINT chk_live_tracking_stop_sequence_state
        CHECK (
            (phase = 'TO_STOP' AND next_stop_sequence IS NOT NULL)
            OR (phase <> 'TO_STOP' AND next_stop_sequence IS NULL)
        );

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
            next_stop_sequence = NULL,
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
