CREATE TABLE wallet_funding_lots (
    id BIGSERIAL PRIMARY KEY,
    wallet_id BIGINT NOT NULL REFERENCES wallets(id) ON DELETE CASCADE,
    source_type VARCHAR(32) NOT NULL,
    source_reference VARCHAR(255) NOT NULL,
    original_amount NUMERIC(19, 2) NOT NULL,
    remaining_amount NUMERIC(19, 2) NOT NULL,
    withdrawable BOOLEAN NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_wallet_funding_lot_source UNIQUE (wallet_id, source_type, source_reference),
    CONSTRAINT ck_wallet_funding_lot_source_type CHECK (
        source_type IN ('STRIPE_PAYMENT', 'EARNED_JOB', 'LEGACY_UNVERIFIED', 'PLATFORM_ADJUSTMENT')
    ),
    CONSTRAINT ck_wallet_funding_lot_original_positive CHECK (original_amount > 0),
    CONSTRAINT ck_wallet_funding_lot_remaining_nonnegative CHECK (remaining_amount >= 0),
    CONSTRAINT ck_wallet_funding_lot_remaining_lte_original CHECK (remaining_amount <= original_amount)
);

CREATE INDEX idx_wallet_funding_lots_available
    ON wallet_funding_lots (wallet_id, withdrawable, created_at, id)
    WHERE remaining_amount > 0;

CREATE TABLE wallet_funding_movements (
    id BIGSERIAL PRIMARY KEY,
    wallet_transaction_id BIGINT NOT NULL REFERENCES wallet_transactions(id) ON DELETE RESTRICT,
    funding_lot_id BIGINT NOT NULL REFERENCES wallet_funding_lots(id) ON DELETE RESTRICT,
    amount NUMERIC(19, 2) NOT NULL,
    restores_movement_id BIGINT REFERENCES wallet_funding_movements(id) ON DELETE RESTRICT,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT ck_wallet_funding_movement_nonzero CHECK (amount <> 0),
    CONSTRAINT ck_wallet_funding_restore_positive CHECK (
        restores_movement_id IS NULL OR amount > 0
    )
);

CREATE INDEX idx_wallet_funding_movements_transaction
    ON wallet_funding_movements (wallet_transaction_id, id);
CREATE INDEX idx_wallet_funding_movements_lot
    ON wallet_funding_movements (funding_lot_id, id);
CREATE INDEX idx_wallet_funding_movements_restore
    ON wallet_funding_movements (restores_movement_id)
    WHERE restores_movement_id IS NOT NULL;

-- Existing wallet history predates source-of-funds accounting. Preserve every current balance,
-- but fail closed for cash-out: legacy money is spendable inside doFast and not withdrawable.
INSERT INTO wallet_funding_lots (
    wallet_id,
    source_type,
    source_reference,
    original_amount,
    remaining_amount,
    withdrawable,
    created_at
)
SELECT
    w.id,
    'LEGACY_UNVERIFIED',
    'legacy:v49:wallet:' || w.id,
    w.balance,
    w.balance,
    FALSE,
    CURRENT_TIMESTAMP
FROM wallets w
WHERE w.balance > 0;
