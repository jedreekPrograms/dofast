CREATE TABLE job_reports (
    id BIGSERIAL PRIMARY KEY,
    job_id BIGINT NOT NULL,
    reporter_id BIGINT NOT NULL,
    reason VARCHAR(32) NOT NULL,
    details VARCHAR(1000),
    status VARCHAR(24) NOT NULL DEFAULT 'SUBMITTED',
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    reviewed_at TIMESTAMP,
    CONSTRAINT fk_job_reports_job FOREIGN KEY (job_id) REFERENCES jobs(id) ON DELETE CASCADE,
    CONSTRAINT fk_job_reports_reporter FOREIGN KEY (reporter_id) REFERENCES users(id) ON DELETE CASCADE,
    CONSTRAINT uk_job_reports_reporter_job UNIQUE (reporter_id, job_id),
    CONSTRAINT ck_job_reports_reason CHECK (reason IN ('SPAM','FRAUD','PROHIBITED_CONTENT','HARASSMENT','OTHER')),
    CONSTRAINT ck_job_reports_status CHECK (status IN ('SUBMITTED','REVIEWED','DISMISSED'))
);

CREATE INDEX idx_job_reports_job_status ON job_reports(job_id, status);
CREATE INDEX idx_job_reports_reporter_created_at ON job_reports(reporter_id, created_at DESC);
