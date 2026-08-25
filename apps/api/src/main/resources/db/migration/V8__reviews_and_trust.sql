ALTER TABLE reviews
    DROP CONSTRAINT uk_reviews_job;

ALTER TABLE reviews
    ADD COLUMN created_at TIMESTAMP(6) WITHOUT TIME ZONE;

UPDATE reviews
SET created_at = CURRENT_TIMESTAMP
WHERE created_at IS NULL;

ALTER TABLE reviews
    ALTER COLUMN created_at SET NOT NULL;

ALTER TABLE reviews
    ADD CONSTRAINT uk_reviews_job_reviewer UNIQUE (job_id, reviewer_id),
    ADD CONSTRAINT chk_reviews_distinct_users CHECK (reviewer_id <> reviewed_id);

CREATE INDEX idx_reviews_reviewed_created
    ON reviews (reviewed_id, created_at DESC, id DESC);

CREATE INDEX idx_reviews_reviewer_created
    ON reviews (reviewer_id, created_at DESC, id DESC);
