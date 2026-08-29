ALTER TABLE job_publications
    ADD COLUMN payment_received_at TIMESTAMP(6) WITHOUT TIME ZONE,
    ADD COLUMN recovery_reason VARCHAR(48);

-- PAYMENT_RECEIVED existed before we persisted an explicit settlement outcome.
-- Backfill only that unambiguous state; historical CANCELLED rows cannot safely
-- be inferred as paid or unpaid from the publication row alone.
UPDATE job_publications
SET payment_received_at = updated_at,
    recovery_reason = 'UNSPECIFIED'
WHERE status = 'PAYMENT_RECEIVED';

ALTER TABLE job_publications
    ADD CONSTRAINT chk_job_publications_recovery_reason CHECK (
        recovery_reason IS NULL OR recovery_reason IN (
            'PUBLICATION_EXPIRED',
            'CATEGORY_UNAVAILABLE',
            'ROUTE_QUOTE_UNAVAILABLE',
            'CANCELLED_BEFORE_PAYMENT_CONFIRMED',
            'UNSPECIFIED'
        )
    ),
    ADD CONSTRAINT chk_job_publications_recovery_state CHECK (
        (status = 'PAYMENT_REQUIRED'
            AND payment_received_at IS NULL
            AND recovery_reason IS NULL)
        OR (status = 'PAYMENT_RECEIVED'
            AND payment_received_at IS NOT NULL
            AND recovery_reason IS NOT NULL)
        OR (status = 'PUBLISHED'
            AND recovery_reason IS NULL)
        OR (status = 'CANCELLED'
            AND (recovery_reason IS NULL OR payment_received_at IS NOT NULL))
    );
