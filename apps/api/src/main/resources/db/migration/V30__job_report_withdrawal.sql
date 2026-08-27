ALTER TABLE job_reports
    ADD COLUMN withdrawn_at TIMESTAMP;

ALTER TABLE job_reports
    DROP CONSTRAINT ck_job_reports_status;

ALTER TABLE job_reports
    ADD CONSTRAINT ck_job_reports_status
        CHECK (status IN ('SUBMITTED','REVIEWED','DISMISSED','WITHDRAWN'));

ALTER TABLE job_reports
    ADD CONSTRAINT ck_job_reports_withdrawal_consistency
        CHECK (
            (status = 'WITHDRAWN' AND withdrawn_at IS NOT NULL)
            OR (status <> 'WITHDRAWN' AND withdrawn_at IS NULL)
        );
