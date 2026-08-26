CREATE TABLE saved_jobs (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    job_id BIGINT NOT NULL REFERENCES jobs(id) ON DELETE CASCADE,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_saved_jobs_user_job UNIQUE (user_id, job_id)
);

CREATE INDEX idx_saved_jobs_user_created_at
    ON saved_jobs (user_id, created_at DESC);

CREATE INDEX idx_saved_jobs_job
    ON saved_jobs (job_id);
