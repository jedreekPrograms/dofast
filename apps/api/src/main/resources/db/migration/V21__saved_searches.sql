CREATE TABLE saved_searches (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL,
    name VARCHAR(80) NOT NULL,
    search_query VARCHAR(100),
    category_id BIGINT,
    min_price NUMERIC(12, 2),
    max_price NUMERIC(12, 2),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_saved_searches_user
        FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
    CONSTRAINT fk_saved_searches_category
        FOREIGN KEY (category_id) REFERENCES job_categories(id) ON DELETE SET NULL,
    CONSTRAINT ck_saved_searches_min_price_non_negative
        CHECK (min_price IS NULL OR min_price >= 0),
    CONSTRAINT ck_saved_searches_max_price_non_negative
        CHECK (max_price IS NULL OR max_price >= 0),
    CONSTRAINT ck_saved_searches_price_range
        CHECK (min_price IS NULL OR max_price IS NULL OR min_price <= max_price)
);

CREATE UNIQUE INDEX uk_saved_searches_user_name_ci
    ON saved_searches(user_id, lower(name));

CREATE INDEX idx_saved_searches_user_updated
    ON saved_searches(user_id, updated_at DESC, id DESC);
