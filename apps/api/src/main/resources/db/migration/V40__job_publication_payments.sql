CREATE TABLE job_publications (
    id BIGSERIAL PRIMARY KEY,
    version INTEGER NOT NULL DEFAULT 0,
    user_id BIGINT NOT NULL REFERENCES users(id),
    request_key VARCHAR(160) NOT NULL,
    payload_hash CHAR(64) NOT NULL,
    request_payload TEXT,
    category_id BIGINT NOT NULL REFERENCES job_categories(id),
    route_quote_id UUID REFERENCES route_quotes(id),
    total_amount NUMERIC(19,2) NOT NULL,
    wallet_reserved_amount NUMERIC(19,2) NOT NULL DEFAULT 0.00,
    payment_amount NUMERIC(19,2) NOT NULL DEFAULT 0.00,
    currency VARCHAR(3) NOT NULL DEFAULT 'PLN',
    status VARCHAR(32) NOT NULL,
    stripe_payment_intent_id VARCHAR(255),
    published_job_id BIGINT REFERENCES jobs(id),
    created_at TIMESTAMP(6) WITHOUT TIME ZONE NOT NULL,
    updated_at TIMESTAMP(6) WITHOUT TIME ZONE NOT NULL,
    expires_at TIMESTAMP(6) WITHOUT TIME ZONE NOT NULL,
    published_at TIMESTAMP(6) WITHOUT TIME ZONE,
    cancelled_at TIMESTAMP(6) WITHOUT TIME ZONE,
    CONSTRAINT uk_job_publications_request_key UNIQUE (request_key),
    CONSTRAINT uk_job_publications_stripe_intent UNIQUE (stripe_payment_intent_id),
    CONSTRAINT uk_job_publications_job UNIQUE (published_job_id),
    CONSTRAINT chk_job_publications_hash CHECK (char_length(payload_hash) = 64),
    CONSTRAINT chk_job_publications_total CHECK (total_amount > 0),
    CONSTRAINT chk_job_publications_reserved CHECK (wallet_reserved_amount >= 0 AND wallet_reserved_amount <= total_amount),
    CONSTRAINT chk_job_publications_payment CHECK (payment_amount >= 0),
    CONSTRAINT chk_job_publications_currency CHECK (currency = upper(currency) AND char_length(currency) = 3),
    CONSTRAINT chk_job_publications_status CHECK (status IN ('PAYMENT_REQUIRED', 'PAYMENT_RECEIVED', 'PUBLISHED', 'CANCELLED')),
    CONSTRAINT chk_job_publications_state CHECK (
        (status = 'PAYMENT_REQUIRED'
            AND request_payload IS NOT NULL
            AND published_job_id IS NULL
            AND published_at IS NULL
            AND cancelled_at IS NULL
            AND payment_amount > 0)
        OR (status = 'PAYMENT_RECEIVED'
            AND request_payload IS NULL
            AND published_job_id IS NULL
            AND published_at IS NULL)
        OR (status = 'PUBLISHED'
            AND request_payload IS NULL
            AND published_job_id IS NOT NULL
            AND published_at IS NOT NULL
            AND cancelled_at IS NULL)
        OR (status = 'CANCELLED'
            AND request_payload IS NULL
            AND published_job_id IS NULL
            AND published_at IS NULL
            AND cancelled_at IS NOT NULL)
    )
);

CREATE INDEX idx_job_publications_user_created
    ON job_publications (user_id, created_at DESC, id DESC);
CREATE INDEX idx_job_publications_expiry
    ON job_publications (status, expires_at, id)
    WHERE status = 'PAYMENT_REQUIRED';
