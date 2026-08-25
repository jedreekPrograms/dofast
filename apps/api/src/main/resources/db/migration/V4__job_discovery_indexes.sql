CREATE EXTENSION IF NOT EXISTS pg_trgm;

CREATE INDEX idx_jobs_open_title_trgm
    ON jobs
    USING GIN (LOWER(title) gin_trgm_ops)
    WHERE status = 'OPEN';

CREATE INDEX idx_jobs_open_description_trgm
    ON jobs
    USING GIN (LOWER(description) gin_trgm_ops)
    WHERE status = 'OPEN';

CREATE INDEX idx_jobs_open_location_label_trgm
    ON jobs
    USING GIN (LOWER(location_label) gin_trgm_ops)
    WHERE status = 'OPEN' AND location_label IS NOT NULL;

CREATE INDEX idx_jobs_open_price_created_at
    ON jobs (price, created_at DESC, id DESC)
    WHERE status = 'OPEN';
