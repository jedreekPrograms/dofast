CREATE TABLE user_service_categories (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL,
    category_id BIGINT NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_user_service_categories_user
        FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
    CONSTRAINT fk_user_service_categories_category
        FOREIGN KEY (category_id) REFERENCES job_categories(id) ON DELETE CASCADE,
    CONSTRAINT uk_user_service_categories_user_category
        UNIQUE (user_id, category_id)
);

CREATE INDEX idx_user_service_categories_category
    ON user_service_categories(category_id);
