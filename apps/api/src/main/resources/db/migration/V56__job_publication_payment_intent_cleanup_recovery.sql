ALTER TABLE job_publications
    ADD COLUMN stripe_cleanup_attempt_count INTEGER NOT NULL DEFAULT 0,
    ADD COLUMN stripe_cleanup_next_attempt_at TIMESTAMP,
    ADD COLUMN stripe_cleanup_completed_at TIMESTAMP,
    ADD COLUMN stripe_cleanup_review_required BOOLEAN NOT NULL DEFAULT FALSE,
    ADD COLUMN stripe_cleanup_last_error VARCHAR(128);

ALTER TABLE job_publications
    ADD CONSTRAINT chk_job_publications_stripe_cleanup_attempt_count
        CHECK (stripe_cleanup_attempt_count >= 0);

-- Historical cancelled publications may have committed local cancellation immediately before
-- the process died, leaving the provider PaymentIntent active. Put them back into the durable
-- cleanup queue unless a successful payment was already observed locally.
UPDATE job_publications
SET stripe_cleanup_next_attempt_at = COALESCE(cancelled_at, updated_at, CURRENT_TIMESTAMP)
WHERE status = 'CANCELLED'
  AND stripe_payment_intent_id IS NOT NULL
  AND BTRIM(stripe_payment_intent_id) <> ''
  AND payment_received_at IS NULL
  AND stripe_cleanup_completed_at IS NULL;

-- A cancelled publication that already recorded a late successful payment has no cancellable
-- PaymentIntent left to clean up. Mark the provider-cleanup work terminal instead of polling it.
UPDATE job_publications
SET stripe_cleanup_completed_at = COALESCE(payment_received_at, updated_at, CURRENT_TIMESTAMP),
    stripe_cleanup_next_attempt_at = NULL
WHERE status = 'CANCELLED'
  AND stripe_payment_intent_id IS NOT NULL
  AND payment_received_at IS NOT NULL;

CREATE INDEX idx_job_publications_stripe_cleanup_due
    ON job_publications (stripe_cleanup_next_attempt_at, id)
    WHERE status = 'CANCELLED'
      AND stripe_payment_intent_id IS NOT NULL
      AND stripe_cleanup_completed_at IS NULL
      AND stripe_cleanup_review_required = FALSE;
