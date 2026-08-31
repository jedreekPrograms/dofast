ALTER TABLE job_publications
    ADD COLUMN stripe_create_started_at TIMESTAMP,
    ADD COLUMN stripe_create_attempt_count INTEGER NOT NULL DEFAULT 0,
    ADD COLUMN stripe_create_next_attempt_at TIMESTAMP,
    ADD COLUMN stripe_create_review_required BOOLEAN NOT NULL DEFAULT FALSE,
    ADD COLUMN stripe_create_last_error VARCHAR(128);

ALTER TABLE job_publications
    ADD CONSTRAINT chk_job_publications_stripe_create_attempt_count
        CHECK (stripe_create_attempt_count >= 0);

-- Intentionally do not backfill stripe_create_started_at for historical rows. Before V57 the
-- application did not persist a durable marker before calling Stripe, so a cancelled publication
-- without a local PaymentIntent id might never have reached Stripe at all. Replaying creation for
-- every historical cancellation would create new provider objects instead of recovering only work
-- that is known to have started.

CREATE INDEX idx_job_publications_stripe_create_recovery_due
    ON job_publications (stripe_create_next_attempt_at, id)
    WHERE status = 'CANCELLED'
      AND stripe_payment_intent_id IS NULL
      AND stripe_create_started_at IS NOT NULL
      AND payment_received_at IS NULL
      AND stripe_create_review_required = FALSE;
