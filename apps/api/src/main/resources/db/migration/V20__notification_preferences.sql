CREATE TABLE notification_preferences (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL,
    notification_type VARCHAR(64) NOT NULL,
    CONSTRAINT fk_notification_preferences_user
        FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
    CONSTRAINT uk_notification_preferences_user_type
        UNIQUE (user_id, notification_type)
);

CREATE INDEX idx_notification_preferences_user
    ON notification_preferences(user_id);
