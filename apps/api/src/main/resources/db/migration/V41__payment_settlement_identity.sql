ALTER TABLE payment_transactions
    ADD COLUMN settlement_purpose VARCHAR(32),
    ADD COLUMN business_reference VARCHAR(128);

UPDATE payment_transactions p
SET settlement_purpose = 'JOB_PUBLICATION',
    business_reference = jp.id::text
FROM job_publications jp
WHERE jp.stripe_payment_intent_id = p.stripe_payment_intent_id;

UPDATE payment_transactions
SET settlement_purpose = 'TOP_UP'
WHERE settlement_purpose IS NULL;

ALTER TABLE payment_transactions
    ALTER COLUMN settlement_purpose SET NOT NULL;

ALTER TABLE payment_transactions
    ADD CONSTRAINT ck_payment_transactions_settlement_purpose
        CHECK (settlement_purpose IN ('TOP_UP', 'JOB_PUBLICATION'));

CREATE INDEX idx_payment_transactions_settlement_identity
    ON payment_transactions (settlement_purpose, business_reference);
