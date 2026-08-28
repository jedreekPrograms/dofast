ALTER TABLE escrow_transactions
    ADD COLUMN platform_fee_basis_points INTEGER NOT NULL DEFAULT 0,
    ADD COLUMN platform_fee_amount NUMERIC(19,2),
    ADD COLUMN payee_amount NUMERIC(19,2);

UPDATE escrow_transactions
SET platform_fee_amount = 0.00,
    payee_amount = amount
WHERE status = 'RELEASED';

ALTER TABLE escrow_transactions
    ADD CONSTRAINT chk_escrow_platform_fee_basis_points
        CHECK (platform_fee_basis_points BETWEEN 0 AND 1000),
    ADD CONSTRAINT chk_escrow_settlement_breakdown CHECK (
        (status = 'RELEASED'
            AND platform_fee_amount IS NOT NULL
            AND platform_fee_amount >= 0
            AND payee_amount IS NOT NULL
            AND payee_amount > 0
            AND platform_fee_amount + payee_amount = amount)
        OR (status IN ('HELD', 'REFUNDED')
            AND platform_fee_amount IS NULL
            AND payee_amount IS NULL)
    );

CREATE TABLE platform_revenue_entries (
    id BIGSERIAL PRIMARY KEY,
    escrow_transaction_id BIGINT NOT NULL,
    job_id BIGINT NOT NULL,
    type VARCHAR(32) NOT NULL,
    amount NUMERIC(19,2) NOT NULL,
    operation_key VARCHAR(160) NOT NULL,
    created_at TIMESTAMP(6) WITHOUT TIME ZONE NOT NULL,
    CONSTRAINT fk_platform_revenue_escrow
        FOREIGN KEY (escrow_transaction_id) REFERENCES escrow_transactions(id),
    CONSTRAINT fk_platform_revenue_job
        FOREIGN KEY (job_id) REFERENCES jobs(id),
    CONSTRAINT uk_platform_revenue_operation UNIQUE (operation_key),
    CONSTRAINT uk_platform_revenue_transaction_type UNIQUE (escrow_transaction_id, type),
    CONSTRAINT chk_platform_revenue_type CHECK (type IN ('PLATFORM_FEE')),
    CONSTRAINT chk_platform_revenue_amount_positive CHECK (amount > 0)
);

CREATE INDEX idx_platform_revenue_job
    ON platform_revenue_entries (job_id);

CREATE INDEX idx_platform_revenue_created
    ON platform_revenue_entries (created_at DESC, id DESC);
