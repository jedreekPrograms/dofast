CREATE TABLE route_quotes (
    id UUID PRIMARY KEY,
    user_id BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    origin GEOGRAPHY(Point, 4326) NOT NULL,
    origin_public_label VARCHAR(120) NOT NULL,
    origin_private_label VARCHAR(200),
    origin_place_id VARCHAR(255),
    destination GEOGRAPHY(Point, 4326) NOT NULL,
    destination_public_label VARCHAR(120) NOT NULL,
    destination_private_label VARCHAR(200),
    destination_place_id VARCHAR(255),
    distance_meters INTEGER NOT NULL CHECK (distance_meters > 0),
    duration_seconds INTEGER NOT NULL CHECK (duration_seconds > 0),
    encoded_polyline TEXT,
    provider VARCHAR(32) NOT NULL,
    created_at TIMESTAMP NOT NULL,
    expires_at TIMESTAMP NOT NULL,
    consumed_at TIMESTAMP,
    version INTEGER NOT NULL DEFAULT 0
);

CREATE INDEX idx_route_quotes_user_created ON route_quotes(user_id, created_at DESC);
CREATE INDEX idx_route_quotes_expires ON route_quotes(expires_at) WHERE consumed_at IS NULL;

ALTER TABLE jobs
    ADD COLUMN destination_location GEOGRAPHY(Point, 4326),
    ADD COLUMN destination_label VARCHAR(120),
    ADD COLUMN destination_private_label VARCHAR(200),
    ADD COLUMN route_distance_meters INTEGER,
    ADD COLUMN route_duration_seconds INTEGER,
    ADD COLUMN route_encoded_polyline TEXT,
    ADD COLUMN route_provider VARCHAR(32),
    ADD COLUMN route_computed_at TIMESTAMP,
    ADD COLUMN route_quote_id UUID REFERENCES route_quotes(id);

CREATE UNIQUE INDEX uk_jobs_route_quote ON jobs(route_quote_id) WHERE route_quote_id IS NOT NULL;
CREATE INDEX idx_jobs_destination_location_gist ON jobs USING GIST(destination_location);

-- Existing single-point jobs remain readable. Their old location is treated as origin A,
-- while destination B is initialized to the same point only as a migration fallback.
UPDATE jobs
SET destination_location = location,
    destination_label = location_label,
    destination_private_label = location_private_label
WHERE destination_location IS NULL;
