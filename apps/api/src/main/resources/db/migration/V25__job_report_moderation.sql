ALTER TABLE job_reports
    ADD COLUMN reviewed_by_id BIGINT,
    ADD COLUMN moderation_note VARCHAR(1000),
    ADD COLUMN version BIGINT NOT NULL DEFAULT 0,
    ADD CONSTRAINT fk_job_reports_reviewed_by
        FOREIGN KEY (reviewed_by_id) REFERENCES users(id) ON DELETE SET NULL;

CREATE INDEX idx_job_reports_status_created_at
    ON job_reports(status, created_at ASC);
