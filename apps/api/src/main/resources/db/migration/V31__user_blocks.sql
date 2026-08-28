CREATE TABLE user_blocks (
    id BIGSERIAL PRIMARY KEY,
    blocker_id BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    blocked_user_id BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT ck_user_blocks_not_self CHECK (blocker_id <> blocked_user_id),
    CONSTRAINT uk_user_blocks_pair UNIQUE (blocker_id, blocked_user_id)
);

CREATE INDEX idx_user_blocks_blocker_created
    ON user_blocks (blocker_id, created_at DESC);

CREATE INDEX idx_user_blocks_blocked_user
    ON user_blocks (blocked_user_id);
