ALTER TABLE users
    ADD COLUMN email_verified_at TIMESTAMP NULL;

-- Preserve access for accounts created before email-verification enforcement.
UPDATE users
SET email_verified_at = COALESCE(updated_at, created_at, CURRENT_TIMESTAMP)
WHERE email_verified_at IS NULL;

CREATE TABLE email_verification_tokens (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL,
    token_hash VARCHAR(64) NOT NULL,
    created_at TIMESTAMP NOT NULL,
    expires_at TIMESTAMP NOT NULL,
    used_at TIMESTAMP NULL,
    invalidated_at TIMESTAMP NULL,
    CONSTRAINT fk_email_verification_tokens_user
        FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
    CONSTRAINT uk_email_verification_tokens_hash UNIQUE (token_hash)
);

CREATE INDEX idx_email_verification_tokens_user_active
    ON email_verification_tokens(user_id, expires_at)
    WHERE used_at IS NULL AND invalidated_at IS NULL;
