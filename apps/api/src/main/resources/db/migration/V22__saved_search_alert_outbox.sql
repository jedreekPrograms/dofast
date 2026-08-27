ALTER TABLE saved_searches
    ADD COLUMN alerts_enabled BOOLEAN NOT NULL DEFAULT FALSE;

CREATE TABLE job_publication_outbox (
    id BIGSERIAL PRIMARY KEY,
    job_id BIGINT NOT NULL UNIQUE REFERENCES jobs(id) ON DELETE CASCADE,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    processed_at TIMESTAMP NULL
);

CREATE INDEX idx_job_publication_outbox_pending
    ON job_publication_outbox (id)
    WHERE processed_at IS NULL;

CREATE TABLE saved_search_alert_deliveries (
    id BIGSERIAL PRIMARY KEY,
    saved_search_id BIGINT NOT NULL REFERENCES saved_searches(id) ON DELETE CASCADE,
    job_id BIGINT NOT NULL REFERENCES jobs(id) ON DELETE CASCADE,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_saved_search_alert_delivery UNIQUE (saved_search_id, job_id)
);

CREATE INDEX idx_saved_search_alert_deliveries_search
    ON saved_search_alert_deliveries (saved_search_id, created_at DESC);
