CREATE TABLE payout_requests (
    id BIGSERIAL PRIMARY KEY,
    version INTEGER NOT NULL DEFAULT 0,
    user_id BIGINT NOT NULL REFERENCES users(id),
    request_key VARCHAR(160) NOT NULL,
    amount NUMERIC(19,2) NOT NULL,
    currency VARCHAR(3) NOT NULL DEFAULT 'PLN',
    status VARCHAR(32) NOT NULL,
    provider_code VARCHAR(32) NOT NULL,
    provider_reference VARCHAR(255),
    attempt_count INTEGER NOT NULL DEFAULT 0,
    requested_at TIMESTAMP(6) WITHOUT TIME ZONE NOT NULL,
    next_attempt_at TIMESTAMP(6) WITHOUT TIME ZONE NOT NULL,
    processing_started_at TIMESTAMP(6) WITHOUT TIME ZONE,
    resolved_at TIMESTAMP(6) WITHOUT TIME ZONE,
    failure_code VARCHAR(64),
    last_error_at TIMESTAMP(6) WITHOUT TIME ZONE,
    CONSTRAINT uk_payout_requests_request_key UNIQUE (request_key),
    CONSTRAINT chk_payout_requests_request_key CHECK (char_length(trim(request_key)) > 0),
    CONSTRAINT chk_payout_requests_amount_positive CHECK (amount > 0),
    CONSTRAINT chk_payout_requests_currency CHECK (char_length(currency) = 3 AND currency = upper(currency)),
    CONSTRAINT chk_payout_requests_provider CHECK (char_length(trim(provider_code)) > 0),
    CONSTRAINT chk_payout_requests_attempt_count CHECK (attempt_count >= 0),
    CONSTRAINT chk_payout_requests_status CHECK (
        status IN ('REQUESTED', 'PROCESSING', 'REVIEW_REQUIRED', 'PAID', 'FAILED', 'CANCELLED')
    ),
    CONSTRAINT chk_payout_requests_state CHECK (
        (status = 'REQUESTED' AND processing_started_at IS NULL AND resolved_at IS NULL)
        OR (status = 'PROCESSING' AND processing_started_at IS NOT NULL AND resolved_at IS NULL)
        OR (status = 'REVIEW_REQUIRED' AND processing_started_at IS NULL AND resolved_at IS NULL)
        OR (status IN ('PAID', 'FAILED', 'CANCELLED') AND processing_started_at IS NULL AND resolved_at IS NOT NULL)
    ),
    CONSTRAINT chk_payout_requests_provider_reference CHECK (
        (status = 'PAID' AND provider_reference IS NOT NULL AND char_length(trim(provider_reference)) > 0)
        OR (status <> 'PAID' AND provider_reference IS NULL)
    )
);

CREATE INDEX idx_payout_requests_user_requested
    ON payout_requests (user_id, requested_at DESC, id DESC);
CREATE INDEX idx_payout_requests_dispatch
    ON payout_requests (provider_code, status, next_attempt_at, requested_at, id);
CREATE INDEX idx_payout_requests_processing
    ON payout_requests (status, processing_started_at)
    WHERE status = 'PROCESSING';
CREATE UNIQUE INDEX uk_payout_requests_provider_reference
    ON payout_requests (provider_code, provider_reference)
    WHERE provider_reference IS NOT NULL;

CREATE TABLE payout_events (
    id BIGSERIAL PRIMARY KEY,
    payout_id BIGINT NOT NULL REFERENCES payout_requests(id) ON DELETE CASCADE,
    event_type VARCHAR(32) NOT NULL,
    source VARCHAR(16) NOT NULL,
    actor_user_id BIGINT REFERENCES users(id),
    note VARCHAR(500),
    created_at TIMESTAMP(6) WITHOUT TIME ZONE NOT NULL,
    CONSTRAINT chk_payout_events_type CHECK (
        event_type IN (
            'REQUESTED', 'PROCESSING_STARTED', 'RETRY_SCHEDULED', 'REVIEW_REQUIRED',
            'PAID', 'FAILED', 'CANCELLED', 'FUNDS_RESTORED', 'ADMIN_RETRY'
        )
    ),
    CONSTRAINT chk_payout_events_source CHECK (source IN ('USER', 'SYSTEM', 'PROVIDER', 'ADMIN'))
);

CREATE INDEX idx_payout_events_payout_created
    ON payout_events (payout_id, created_at, id);
