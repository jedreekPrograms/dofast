CREATE TABLE users (
    id BIGINT NOT NULL AUTO_INCREMENT,
    email VARCHAR(320) NOT NULL,
    nickname VARCHAR(80) NOT NULL,
    password VARCHAR(255) NOT NULL,
    CONSTRAINT pk_users PRIMARY KEY (id),
    CONSTRAINT uk_users_email UNIQUE (email)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE jobs (
    id BIGINT NOT NULL AUTO_INCREMENT,
    title VARCHAR(160) NOT NULL,
    description VARCHAR(4000) NOT NULL,
    price DECIMAL(19,2) NOT NULL,
    status VARCHAR(32) NOT NULL,
    created_by_id BIGINT NOT NULL,
    taken_by_id BIGINT NULL,
    CONSTRAINT pk_jobs PRIMARY KEY (id),
    CONSTRAINT fk_jobs_created_by FOREIGN KEY (created_by_id) REFERENCES users (id),
    CONSTRAINT fk_jobs_taken_by FOREIGN KEY (taken_by_id) REFERENCES users (id),
    CONSTRAINT chk_jobs_price_positive CHECK (price > 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE INDEX idx_jobs_status ON jobs (status);
CREATE INDEX idx_jobs_created_by ON jobs (created_by_id);
CREATE INDEX idx_jobs_taken_by ON jobs (taken_by_id);

CREATE TABLE wallets (
    id BIGINT NOT NULL AUTO_INCREMENT,
    user_id BIGINT NOT NULL,
    balance DECIMAL(19,2) NOT NULL DEFAULT 0.00,
    CONSTRAINT pk_wallets PRIMARY KEY (id),
    CONSTRAINT uk_wallets_user UNIQUE (user_id),
    CONSTRAINT fk_wallets_user FOREIGN KEY (user_id) REFERENCES users (id),
    CONSTRAINT chk_wallets_balance_nonnegative CHECK (balance >= 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE wallet_transactions (
    id BIGINT NOT NULL AUTO_INCREMENT,
    wallet_id BIGINT NOT NULL,
    type VARCHAR(32) NOT NULL,
    amount DECIMAL(19,2) NOT NULL,
    job_id BIGINT NULL,
    created_at DATETIME(6) NOT NULL,
    CONSTRAINT pk_wallet_transactions PRIMARY KEY (id),
    CONSTRAINT fk_wallet_transactions_wallet FOREIGN KEY (wallet_id) REFERENCES wallets (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE INDEX idx_wallet_transactions_wallet_created ON wallet_transactions (wallet_id, created_at);
CREATE INDEX idx_wallet_transactions_job ON wallet_transactions (job_id);

CREATE TABLE escrow_transactions (
    id BIGINT NOT NULL AUTO_INCREMENT,
    job_id BIGINT NOT NULL,
    payer_id BIGINT NOT NULL,
    payee_id BIGINT NULL,
    amount DECIMAL(19,2) NOT NULL,
    status VARCHAR(32) NOT NULL,
    CONSTRAINT pk_escrow_transactions PRIMARY KEY (id),
    CONSTRAINT uk_escrow_transactions_job UNIQUE (job_id),
    CONSTRAINT fk_escrow_transactions_job FOREIGN KEY (job_id) REFERENCES jobs (id),
    CONSTRAINT fk_escrow_transactions_payer FOREIGN KEY (payer_id) REFERENCES users (id),
    CONSTRAINT fk_escrow_transactions_payee FOREIGN KEY (payee_id) REFERENCES users (id),
    CONSTRAINT chk_escrow_transactions_amount_positive CHECK (amount > 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE INDEX idx_escrow_transactions_payer ON escrow_transactions (payer_id);
CREATE INDEX idx_escrow_transactions_payee ON escrow_transactions (payee_id);

CREATE TABLE payment_transactions (
    id BIGINT NOT NULL AUTO_INCREMENT,
    stripe_payment_intent_id VARCHAR(255) NOT NULL,
    user_id BIGINT NOT NULL,
    amount DECIMAL(19,2) NOT NULL,
    CONSTRAINT pk_payment_transactions PRIMARY KEY (id),
    CONSTRAINT uk_payment_transactions_stripe_intent UNIQUE (stripe_payment_intent_id),
    CONSTRAINT fk_payment_transactions_user FOREIGN KEY (user_id) REFERENCES users (id),
    CONSTRAINT chk_payment_transactions_amount_positive CHECK (amount > 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE INDEX idx_payment_transactions_user ON payment_transactions (user_id);

CREATE TABLE reviews (
    id BIGINT NOT NULL AUTO_INCREMENT,
    job_id BIGINT NOT NULL,
    reviewer_id BIGINT NOT NULL,
    reviewed_id BIGINT NOT NULL,
    rating INT NOT NULL,
    comment VARCHAR(2000) NULL,
    CONSTRAINT pk_reviews PRIMARY KEY (id),
    CONSTRAINT uk_reviews_job UNIQUE (job_id),
    CONSTRAINT fk_reviews_job FOREIGN KEY (job_id) REFERENCES jobs (id),
    CONSTRAINT fk_reviews_reviewer FOREIGN KEY (reviewer_id) REFERENCES users (id),
    CONSTRAINT fk_reviews_reviewed FOREIGN KEY (reviewed_id) REFERENCES users (id),
    CONSTRAINT chk_reviews_rating CHECK (rating BETWEEN 1 AND 5)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE INDEX idx_reviews_reviewed ON reviews (reviewed_id);

CREATE TABLE chat_messages (
    id BIGINT NOT NULL AUTO_INCREMENT,
    job_id BIGINT NOT NULL,
    sender_id BIGINT NOT NULL,
    content VARCHAR(4000) NOT NULL,
    created_at DATETIME(6) NOT NULL,
    CONSTRAINT pk_chat_messages PRIMARY KEY (id),
    CONSTRAINT fk_chat_messages_job FOREIGN KEY (job_id) REFERENCES jobs (id),
    CONSTRAINT fk_chat_messages_sender FOREIGN KEY (sender_id) REFERENCES users (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE INDEX idx_chat_messages_job_created ON chat_messages (job_id, created_at);
CREATE INDEX idx_chat_messages_sender ON chat_messages (sender_id);
