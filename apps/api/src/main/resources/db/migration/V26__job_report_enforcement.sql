CREATE TABLE job_report_enforcements (
    id BIGSERIAL PRIMARY KEY,
    report_id BIGINT NOT NULL,
    job_id BIGINT NOT NULL,
    moderator_id BIGINT NOT NULL,
    action VARCHAR(32) NOT NULL,
    reason VARCHAR(1000),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_job_report_enforcements_report FOREIGN KEY (report_id) REFERENCES job_reports(id) ON DELETE CASCADE,
    CONSTRAINT fk_job_report_enforcements_job FOREIGN KEY (job_id) REFERENCES jobs(id) ON DELETE CASCADE,
    CONSTRAINT fk_job_report_enforcements_moderator FOREIGN KEY (moderator_id) REFERENCES users(id),
    CONSTRAINT uk_job_report_enforcements_report UNIQUE (report_id),
    CONSTRAINT chk_job_report_enforcements_action CHECK (action IN ('CANCEL_OPEN_JOB'))
);

CREATE INDEX idx_job_report_enforcements_job ON job_report_enforcements(job_id);
CREATE INDEX idx_job_report_enforcements_moderator ON job_report_enforcements(moderator_id);
