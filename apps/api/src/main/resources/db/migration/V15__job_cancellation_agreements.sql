CREATE TABLE job_cancellation_requests (
    id BIGSERIAL PRIMARY KEY,
    version INTEGER NOT NULL DEFAULT 0,
    job_id BIGINT NOT NULL REFERENCES jobs(id) ON DELETE CASCADE,
    requested_by_id BIGINT NOT NULL REFERENCES users(id),
    resolved_by_id BIGINT REFERENCES users(id),
    reason VARCHAR(1000) NOT NULL,
    status VARCHAR(24) NOT NULL,
    requested_at TIMESTAMP NOT NULL,
    resolved_at TIMESTAMP,
    CONSTRAINT ck_job_cancellation_status CHECK (status IN ('PENDING', 'APPROVED', 'DECLINED', 'WITHDRAWN')),
    CONSTRAINT ck_job_cancellation_reason_not_blank CHECK (length(trim(reason)) > 0)
);

CREATE INDEX idx_job_cancellation_job_requested
    ON job_cancellation_requests(job_id, requested_at DESC);

CREATE INDEX idx_job_cancellation_requested_by
    ON job_cancellation_requests(requested_by_id, requested_at DESC);

CREATE UNIQUE INDEX uk_job_cancellation_one_pending_per_job
    ON job_cancellation_requests(job_id)
    WHERE status = 'PENDING';
