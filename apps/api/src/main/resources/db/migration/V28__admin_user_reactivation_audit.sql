CREATE TABLE admin_user_reactivation_audits (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL REFERENCES users(id),
    admin_id BIGINT NOT NULL REFERENCES users(id),
    previous_status VARCHAR(20) NOT NULL,
    new_status VARCHAR(20) NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT ck_admin_user_reactivation_statuses
        CHECK (previous_status = 'SUSPENDED' AND new_status = 'ACTIVE')
);

CREATE INDEX idx_admin_user_reactivation_audits_user_created
    ON admin_user_reactivation_audits(user_id, created_at DESC);

CREATE INDEX idx_admin_user_reactivation_audits_admin_created
    ON admin_user_reactivation_audits(admin_id, created_at DESC);
