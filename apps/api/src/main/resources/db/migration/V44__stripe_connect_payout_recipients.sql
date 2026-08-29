CREATE TABLE payout_recipient_accounts (
    id BIGSERIAL PRIMARY KEY,
    version INTEGER NOT NULL DEFAULT 0,
    user_id BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    provider_code VARCHAR(32) NOT NULL,
    provider_account_id VARCHAR(255) NOT NULL,
    details_submitted BOOLEAN NOT NULL DEFAULT FALSE,
    payouts_enabled BOOLEAN NOT NULL DEFAULT FALSE,
    transfers_enabled BOOLEAN NOT NULL DEFAULT FALSE,
    requirements_due BOOLEAN NOT NULL DEFAULT TRUE,
    last_synced_at TIMESTAMP(6) WITHOUT TIME ZONE,
    created_at TIMESTAMP(6) WITHOUT TIME ZONE NOT NULL,
    updated_at TIMESTAMP(6) WITHOUT TIME ZONE NOT NULL,
    CONSTRAINT uk_payout_recipient_user_provider UNIQUE (user_id, provider_code),
    CONSTRAINT uk_payout_recipient_provider_account UNIQUE (provider_code, provider_account_id),
    CONSTRAINT chk_payout_recipient_provider CHECK (char_length(trim(provider_code)) > 0),
    CONSTRAINT chk_payout_recipient_account CHECK (char_length(trim(provider_account_id)) > 0)
);

CREATE INDEX idx_payout_recipient_ready
    ON payout_recipient_accounts (provider_code, payouts_enabled, transfers_enabled, requirements_due);
