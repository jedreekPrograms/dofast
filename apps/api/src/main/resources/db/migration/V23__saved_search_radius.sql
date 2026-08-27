ALTER TABLE saved_searches
    ADD COLUMN center_location geography(Point,4326),
    ADD COLUMN radius_meters integer;

ALTER TABLE saved_searches
    ADD CONSTRAINT ck_saved_searches_location_radius_pair
        CHECK ((center_location IS NULL AND radius_meters IS NULL)
            OR (center_location IS NOT NULL AND radius_meters BETWEEN 1000 AND 100000));

CREATE INDEX idx_saved_searches_center_location
    ON saved_searches USING GIST (center_location)
    WHERE center_location IS NOT NULL;
