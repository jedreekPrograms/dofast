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
