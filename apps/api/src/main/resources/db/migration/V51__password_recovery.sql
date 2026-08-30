ALTER TABLE users
    ADD COLUMN auth_version BIGINT NOT NULL DEFAULT 0;

ALTER TABLE users
    ADD CONSTRAINT ck_users_auth_version_nonnegative
    CHECK (auth_version >= 0);

CREATE TABLE auth_password_reset_tokens (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL,
    token_hash VARCHAR(64) NOT NULL,
    created_at TIMESTAMP NOT NULL,
    expires_at TIMESTAMP NOT NULL,
    used_at TIMESTAMP NULL,
    invalidated_at TIMESTAMP NULL,
    CONSTRAINT uk_auth_password_reset_tokens_hash UNIQUE (token_hash),
    CONSTRAINT fk_auth_password_reset_tokens_user
        FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
    CONSTRAINT ck_auth_password_reset_tokens_lifecycle
        CHECK (expires_at > created_at)
);

CREATE INDEX idx_auth_password_reset_tokens_user_active
    ON auth_password_reset_tokens(user_id, expires_at)
    WHERE used_at IS NULL AND invalidated_at IS NULL;
