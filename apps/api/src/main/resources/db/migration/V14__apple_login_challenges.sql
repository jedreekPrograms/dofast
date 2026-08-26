CREATE TABLE apple_login_challenges (
    id UUID NOT NULL,
    state_hash CHAR(64) NOT NULL,
    nonce_hash CHAR(64) NOT NULL,
    expires_at TIMESTAMP WITH TIME ZONE NOT NULL,
    consumed_at TIMESTAMP WITH TIME ZONE NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT pk_apple_login_challenges PRIMARY KEY (id)
);

CREATE INDEX idx_apple_login_challenges_expires_at
    ON apple_login_challenges (expires_at);

CREATE INDEX idx_apple_login_challenges_unconsumed
    ON apple_login_challenges (expires_at)
    WHERE consumed_at IS NULL;