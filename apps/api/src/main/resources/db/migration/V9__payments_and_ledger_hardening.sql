ALTER TABLE wallet_transactions
    ADD COLUMN operation_key VARCHAR(160),
    ADD COLUMN balance_after NUMERIC(19,2);

UPDATE wallet_transactions
SET operation_key = 'legacy:' || id
WHERE operation_key IS NULL;

WITH running_balances AS (
    SELECT
        id,
        SUM(amount) OVER (
            PARTITION BY wallet_id
            ORDER BY created_at, id
            ROWS BETWEEN UNBOUNDED PRECEDING AND CURRENT ROW
        ) AS balance_after
    FROM wallet_transactions
)
UPDATE wallet_transactions wt
SET balance_after = running_balances.balance_after
FROM running_balances
WHERE running_balances.id = wt.id;

ALTER TABLE wallet_transactions
    ALTER COLUMN operation_key SET NOT NULL,
    ALTER COLUMN balance_after SET NOT NULL,
    ADD CONSTRAINT uk_wallet_transactions_operation UNIQUE (operation_key),
    ADD CONSTRAINT chk_wallet_transactions_amount_nonzero CHECK (amount <> 0),
    ADD CONSTRAINT chk_wallet_transactions_balance_after_nonnegative CHECK (balance_after >= 0),
    ADD CONSTRAINT fk_wallet_transactions_job FOREIGN KEY (job_id) REFERENCES jobs (id);

ALTER TABLE escrow_transactions
    ADD COLUMN version INTEGER NOT NULL DEFAULT 0,
    ADD COLUMN held_at TIMESTAMP(6) WITHOUT TIME ZONE,
    ADD COLUMN resolved_at TIMESTAMP(6) WITHOUT TIME ZONE;

UPDATE escrow_transactions e
SET held_at = COALESCE(j.created_at, CURRENT_TIMESTAMP)
FROM jobs j
WHERE e.job_id = j.id
  AND e.held_at IS NULL;

UPDATE escrow_transactions e
SET resolved_at = COALESCE(j.completed_at, j.cancelled_at, CURRENT_TIMESTAMP)
FROM jobs j
WHERE e.job_id = j.id
  AND e.status IN ('RELEASED', 'REFUNDED')
  AND e.resolved_at IS NULL;

ALTER TABLE escrow_transactions
    ALTER COLUMN held_at SET NOT NULL,
    ADD CONSTRAINT chk_escrow_resolution_shape CHECK (
        (status = 'HELD' AND resolved_at IS NULL AND payee_id IS NULL)
        OR (status = 'RELEASED' AND resolved_at IS NOT NULL AND payee_id IS NOT NULL)
        OR (status = 'REFUNDED' AND resolved_at IS NOT NULL AND payee_id IS NULL)
    );

CREATE INDEX idx_escrow_transactions_status
    ON escrow_transactions (status);

ALTER TABLE payment_transactions
    ADD COLUMN stripe_event_id VARCHAR(255),
    ADD COLUMN currency VARCHAR(3),
    ADD COLUMN processed_at TIMESTAMP(6) WITHOUT TIME ZONE;

UPDATE payment_transactions
SET stripe_event_id = 'legacy-event:' || id,
    currency = 'PLN',
    processed_at = CURRENT_TIMESTAMP
WHERE stripe_event_id IS NULL
   OR currency IS NULL
   OR processed_at IS NULL;

ALTER TABLE payment_transactions
    ALTER COLUMN stripe_event_id SET NOT NULL,
    ALTER COLUMN currency SET NOT NULL,
    ALTER COLUMN processed_at SET NOT NULL,
    ADD CONSTRAINT uk_payment_transactions_stripe_event UNIQUE (stripe_event_id),
    ADD CONSTRAINT chk_payment_transactions_currency CHECK (
        char_length(currency) = 3 AND currency = upper(currency)
    );

CREATE INDEX idx_payment_transactions_processed
    ON payment_transactions (processed_at DESC, id DESC);
