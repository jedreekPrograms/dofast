ALTER TABLE payout_requests
    ADD COLUMN provider_submitted_at TIMESTAMP(6) WITHOUT TIME ZONE;

ALTER TABLE payout_requests
    DROP CONSTRAINT chk_payout_requests_status,
    DROP CONSTRAINT chk_payout_requests_state,
    DROP CONSTRAINT chk_payout_requests_provider_reference;

ALTER TABLE payout_requests
    ADD CONSTRAINT chk_payout_requests_status CHECK (
        status IN ('REQUESTED', 'PROCESSING', 'SUBMITTED', 'REVIEW_REQUIRED', 'PAID', 'FAILED', 'CANCELLED')
    ),
    ADD CONSTRAINT chk_payout_requests_state CHECK (
        (status = 'REQUESTED' AND processing_started_at IS NULL AND resolved_at IS NULL)
        OR (status = 'PROCESSING' AND processing_started_at IS NOT NULL AND resolved_at IS NULL)
        OR (status = 'SUBMITTED' AND processing_started_at IS NULL AND provider_submitted_at IS NOT NULL AND resolved_at IS NULL)
        OR (status = 'REVIEW_REQUIRED' AND processing_started_at IS NULL AND resolved_at IS NULL)
        OR (status IN ('PAID', 'FAILED', 'CANCELLED') AND processing_started_at IS NULL AND resolved_at IS NOT NULL)
    ),
    ADD CONSTRAINT chk_payout_requests_provider_reference CHECK (
        (provider_reference IS NULL OR char_length(trim(provider_reference)) > 0)
        AND (
            (status IN ('SUBMITTED', 'PAID') AND provider_reference IS NOT NULL)
            OR status = 'FAILED'
            OR (status NOT IN ('SUBMITTED', 'PAID', 'FAILED') AND provider_reference IS NULL)
        )
        AND (provider_submitted_at IS NULL OR (provider_reference IS NOT NULL AND status IN ('SUBMITTED', 'PAID', 'FAILED')))
    );

ALTER TABLE payout_events
    DROP CONSTRAINT chk_payout_events_type;

ALTER TABLE payout_events
    ADD CONSTRAINT chk_payout_events_type CHECK (
        event_type IN (
            'REQUESTED', 'PROCESSING_STARTED', 'SUBMITTED', 'RETRY_SCHEDULED', 'REVIEW_REQUIRED',
            'PAID', 'FAILED', 'CANCELLED', 'FUNDS_RESTORED', 'ADMIN_RETRY'
        )
    );

CREATE TABLE payout_provider_events (
    id BIGSERIAL PRIMARY KEY,
    payout_id BIGINT NOT NULL REFERENCES payout_requests(id) ON DELETE CASCADE,
    provider_code VARCHAR(32) NOT NULL,
    provider_event_id VARCHAR(255) NOT NULL,
    provider_reference VARCHAR(255) NOT NULL,
    outcome VARCHAR(16) NOT NULL,
    failure_code VARCHAR(64),
    received_at TIMESTAMP(6) WITHOUT TIME ZONE NOT NULL,
    CONSTRAINT uk_payout_provider_event UNIQUE (provider_code, provider_event_id),
    CONSTRAINT chk_payout_provider_event_provider CHECK (char_length(trim(provider_code)) > 0),
    CONSTRAINT chk_payout_provider_event_id CHECK (char_length(trim(provider_event_id)) > 0),
    CONSTRAINT chk_payout_provider_event_reference CHECK (char_length(trim(provider_reference)) > 0),
    CONSTRAINT chk_payout_provider_event_outcome CHECK (outcome IN ('PAID', 'FAILED')),
    CONSTRAINT chk_payout_provider_event_failure CHECK (
        (outcome = 'PAID' AND failure_code IS NULL)
        OR (outcome = 'FAILED' AND failure_code IS NOT NULL AND char_length(trim(failure_code)) > 0)
    )
);

CREATE INDEX idx_payout_provider_events_payout_received
    ON payout_provider_events (payout_id, received_at, id);
