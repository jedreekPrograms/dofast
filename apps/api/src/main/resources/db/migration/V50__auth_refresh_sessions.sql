CREATE TABLE auth_refresh_sessions (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    family_id UUID NOT NULL,
    token_hash CHAR(64) NOT NULL,
    csrf_hash CHAR(64) NOT NULL,
    created_at TIMESTAMP WITHOUT TIME ZONE NOT NULL,
    expires_at TIMESTAMP WITHOUT TIME ZONE NOT NULL,
    last_used_at TIMESTAMP WITHOUT TIME ZONE,
    revoked_at TIMESTAMP WITHOUT TIME ZONE,
    revocation_reason VARCHAR(32),
    CONSTRAINT uk_auth_refresh_sessions_token_hash UNIQUE (token_hash)
);

CREATE INDEX idx_auth_refresh_sessions_user_active
    ON auth_refresh_sessions (user_id, expires_at)
    WHERE revoked_at IS NULL;

CREATE INDEX idx_auth_refresh_sessions_family_active
    ON auth_refresh_sessions (family_id, expires_at)
    WHERE revoked_at IS NULL;

CREATE INDEX idx_auth_refresh_sessions_expiry
    ON auth_refresh_sessions (expires_at);
