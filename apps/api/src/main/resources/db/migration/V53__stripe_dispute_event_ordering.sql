ALTER TABLE stripe_payment_disputes
    ADD COLUMN stripe_state_event_created_at TIMESTAMP(6) WITHOUT TIME ZONE;

-- Historical rows predate event-order tracking. Their current state remains authoritative until
-- the next signed Stripe dispute event establishes an ordering watermark.
CREATE INDEX idx_stripe_payment_disputes_state_event_created
    ON stripe_payment_disputes (stripe_state_event_created_at)
    WHERE stripe_state_event_created_at IS NOT NULL;
