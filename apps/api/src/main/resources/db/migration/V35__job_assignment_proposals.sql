ALTER TABLE jobs
    ADD COLUMN assignment_mode VARCHAR(20) NOT NULL DEFAULT 'INSTANT',
    ADD COLUMN price_negotiation_enabled BOOLEAN NOT NULL DEFAULT FALSE;

ALTER TABLE jobs
    ADD CONSTRAINT ck_jobs_assignment_mode
        CHECK (assignment_mode IN ('INSTANT', 'PROPOSALS')),
    ADD CONSTRAINT ck_jobs_negotiation_requires_proposals
        CHECK (price_negotiation_enabled = FALSE OR assignment_mode = 'PROPOSALS');

CREATE TABLE job_proposals (
    id BIGSERIAL PRIMARY KEY,
    version INTEGER NOT NULL DEFAULT 0,
    job_id BIGINT NOT NULL,
    proposer_id BIGINT NOT NULL,
    amount NUMERIC(19,2) NOT NULL,
    message VARCHAR(1000),
    status VARCHAR(20) NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    accepted_at TIMESTAMP NULL,
    withdrawn_at TIMESTAMP NULL,
    CONSTRAINT fk_job_proposals_job
        FOREIGN KEY (job_id) REFERENCES jobs(id) ON DELETE CASCADE,
    CONSTRAINT fk_job_proposals_proposer
        FOREIGN KEY (proposer_id) REFERENCES users(id) ON DELETE CASCADE,
    CONSTRAINT uq_job_proposals_job_proposer UNIQUE (job_id, proposer_id),
    CONSTRAINT ck_job_proposals_amount CHECK (amount > 0),
    CONSTRAINT ck_job_proposals_status
        CHECK (status IN ('SUBMITTED', 'ACCEPTED', 'REJECTED', 'WITHDRAWN'))
);

CREATE INDEX idx_job_proposals_job_status_created
    ON job_proposals(job_id, status, created_at DESC, id DESC);

CREATE INDEX idx_job_proposals_proposer_created
    ON job_proposals(proposer_id, created_at DESC, id DESC);
