CREATE TABLE job_attachments (
    id BIGSERIAL PRIMARY KEY,
    version INTEGER NOT NULL DEFAULT 0,
    job_id BIGINT NOT NULL,
    uploaded_by_id BIGINT NOT NULL,
    visibility VARCHAR(32) NOT NULL,
    original_filename VARCHAR(180) NOT NULL,
    media_type VARCHAR(80) NOT NULL,
    size_bytes BIGINT NOT NULL,
    sha256 VARCHAR(64) NOT NULL,
    storage_key VARCHAR(64) NOT NULL,
    created_at TIMESTAMP NOT NULL,
    deleted_at TIMESTAMP,
    CONSTRAINT fk_job_attachments_job
        FOREIGN KEY (job_id) REFERENCES jobs(id) ON DELETE CASCADE,
    CONSTRAINT fk_job_attachments_uploaded_by
        FOREIGN KEY (uploaded_by_id) REFERENCES users(id),
    CONSTRAINT uq_job_attachments_storage_key UNIQUE (storage_key),
    CONSTRAINT ck_job_attachments_visibility
        CHECK (visibility IN ('JOB_VIEWERS', 'PARTICIPANTS', 'EXECUTION_SECRET')),
    CONSTRAINT ck_job_attachments_size
        CHECK (size_bytes BETWEEN 1 AND 10485760),
    CONSTRAINT ck_job_attachments_sha256
        CHECK (sha256 ~ '^[0-9a-f]{64}$')
);

CREATE INDEX idx_job_attachments_job_active
    ON job_attachments (job_id, created_at, id)
    WHERE deleted_at IS NULL;

CREATE INDEX idx_job_attachments_uploaded_by
    ON job_attachments (uploaded_by_id);
