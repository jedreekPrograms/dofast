ALTER TABLE jobs
    ADD COLUMN expense_budget NUMERIC(19,2) NOT NULL DEFAULT 0.00;

ALTER TABLE jobs
    ADD CONSTRAINT chk_jobs_expense_budget
    CHECK (expense_budget >= 0.00 AND expense_budget <= 10000.00);

CREATE TABLE job_expense_escrows (
    id BIGSERIAL PRIMARY KEY,
    version INTEGER NOT NULL DEFAULT 0,
    job_id BIGINT NOT NULL,
    payer_id BIGINT NOT NULL,
    budget_amount NUMERIC(19,2) NOT NULL,
    claimed_amount NUMERIC(19,2) NOT NULL DEFAULT 0.00,
    reimbursed_amount NUMERIC(19,2) NOT NULL DEFAULT 0.00,
    refunded_amount NUMERIC(19,2) NOT NULL DEFAULT 0.00,
    status VARCHAR(32) NOT NULL,
    held_at TIMESTAMP NOT NULL,
    resolved_at TIMESTAMP,
    CONSTRAINT uk_job_expense_escrows_job UNIQUE (job_id),
    CONSTRAINT fk_job_expense_escrows_job FOREIGN KEY (job_id) REFERENCES jobs(id),
    CONSTRAINT fk_job_expense_escrows_payer FOREIGN KEY (payer_id) REFERENCES users(id),
    CONSTRAINT chk_job_expense_escrows_budget CHECK (budget_amount > 0.00 AND budget_amount <= 10000.00),
    CONSTRAINT chk_job_expense_escrows_claimed CHECK (claimed_amount >= 0.00 AND claimed_amount <= budget_amount),
    CONSTRAINT chk_job_expense_escrows_reimbursed CHECK (reimbursed_amount >= 0.00 AND reimbursed_amount <= budget_amount),
    CONSTRAINT chk_job_expense_escrows_refunded CHECK (refunded_amount >= 0.00 AND refunded_amount <= budget_amount),
    CONSTRAINT chk_job_expense_escrows_status CHECK (status IN ('HELD', 'SETTLED', 'REFUNDED')),
    CONSTRAINT chk_job_expense_escrows_resolution CHECK (
        (status = 'HELD' AND reimbursed_amount = 0.00 AND refunded_amount = 0.00 AND resolved_at IS NULL)
        OR
        (status = 'SETTLED' AND reimbursed_amount + refunded_amount = budget_amount AND resolved_at IS NOT NULL)
        OR
        (status = 'REFUNDED' AND reimbursed_amount = 0.00 AND refunded_amount = budget_amount AND resolved_at IS NOT NULL)
    )
);

CREATE INDEX idx_job_expense_escrows_payer ON job_expense_escrows(payer_id);
CREATE INDEX idx_job_expense_escrows_status ON job_expense_escrows(status);

CREATE TABLE job_expense_claims (
    id BIGSERIAL PRIMARY KEY,
    version INTEGER NOT NULL DEFAULT 0,
    job_id BIGINT NOT NULL,
    worker_id BIGINT NOT NULL,
    attachment_id BIGINT NOT NULL,
    amount NUMERIC(19,2) NOT NULL,
    created_at TIMESTAMP NOT NULL,
    CONSTRAINT uk_job_expense_claims_attachment UNIQUE (attachment_id),
    CONSTRAINT fk_job_expense_claims_job FOREIGN KEY (job_id) REFERENCES jobs(id),
    CONSTRAINT fk_job_expense_claims_worker FOREIGN KEY (worker_id) REFERENCES users(id),
    CONSTRAINT fk_job_expense_claims_attachment FOREIGN KEY (attachment_id) REFERENCES job_attachments(id),
    CONSTRAINT chk_job_expense_claims_amount CHECK (amount > 0.00 AND amount <= 10000.00)
);

CREATE INDEX idx_job_expense_claims_job ON job_expense_claims(job_id, created_at, id);
CREATE INDEX idx_job_expense_claims_worker ON job_expense_claims(worker_id);
