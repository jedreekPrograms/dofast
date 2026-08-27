CREATE TABLE job_report_account_enforcements (
    id BIGSERIAL PRIMARY KEY,
    report_id BIGINT NOT NULL,
    target_user_id BIGINT NOT NULL,
    moderator_id BIGINT NOT NULL,
    action VARCHAR(40) NOT NULL,
    reason VARCHAR(1000),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_job_report_account_enforcements_report FOREIGN KEY (report_id) REFERENCES job_reports(id) ON DELETE CASCADE,
    CONSTRAINT fk_job_report_account_enforcements_target FOREIGN KEY (target_user_id) REFERENCES users(id),
    CONSTRAINT fk_job_report_account_enforcements_moderator FOREIGN KEY (moderator_id) REFERENCES users(id),
    CONSTRAINT uk_job_report_account_enforcements_report UNIQUE (report_id),
    CONSTRAINT chk_job_report_account_enforcements_action CHECK (action IN ('SUSPEND_JOB_OWNER'))
);

CREATE INDEX idx_job_report_account_enforcements_target ON job_report_account_enforcements(target_user_id);
CREATE INDEX idx_job_report_account_enforcements_moderator ON job_report_account_enforcements(moderator_id);
