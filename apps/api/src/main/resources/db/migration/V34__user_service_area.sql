CREATE TABLE user_service_areas (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL,
    center_location geography(Point, 4326) NOT NULL,
    radius_meters INTEGER NOT NULL,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_user_service_areas_user
        FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
    CONSTRAINT uq_user_service_areas_user UNIQUE (user_id),
    CONSTRAINT ck_user_service_areas_radius
        CHECK (radius_meters BETWEEN 1000 AND 100000)
);

CREATE INDEX idx_user_service_areas_center_gist
    ON user_service_areas USING GIST (center_location);
