#!/usr/bin/env bash
set -euo pipefail

if test "$#" -lt 5 || test "$#" -gt 6; then
  echo "usage: $0 <user_id> <amount> <source_type> <source_reference> <operation_key> [wallet_transaction_type]" >&2
  exit 2
fi

user_id="$1"
amount="$2"
source_type="$3"
source_reference="$4"
operation_key="$5"
transaction_type="${6:-TOP_UP}"

if [[ ! "$user_id" =~ ^[0-9]+$ ]]; then
  echo "user_id must be a positive integer" >&2
  exit 2
fi
if [[ ! "$amount" =~ ^[0-9]+([.][0-9]{1,2})?$ ]] || [[ "$amount" =~ ^0+([.]0{1,2})?$ ]]; then
  echo "amount must be a positive decimal with at most two fractional digits" >&2
  exit 2
fi
case "$source_type" in
  STRIPE_PAYMENT|EARNED_JOB|LEGACY_UNVERIFIED|PLATFORM_ADJUSTMENT) ;;
  *)
    echo "unsupported funding source type: $source_type" >&2
    exit 2
    ;;
esac

# Resolve the fixture target before mutating anything. Avoid SQL expressions such as ELSE 1/0:
# PostgreSQL may constant-fold them even when the branch is unreachable.
wallet_count=$(docker compose exec -T db psql -U dofast -d dofast -tAc \
  "SELECT count(*) FROM wallets WHERE user_id=$user_id;" | tr -d '[:space:]')
if test "$wallet_count" != "1"; then
  echo "expected exactly one wallet for user_id=$user_id, found ${wallet_count:-0}" >&2
  exit 3
fi

# CI-only helper. It creates the same four accounting facts that production wallet credits create:
# wallet balance, wallet transaction, funding lot, and funding movement. This keeps smoke fixtures
# from bypassing the source-of-funds invariant introduced by V49.
docker compose exec -T db psql -U dofast -d dofast -v ON_ERROR_STOP=1 \
  -v user_id="$user_id" \
  -v amount="$amount" \
  -v source_type="$source_type" \
  -v source_reference="$source_reference" \
  -v operation_key="$operation_key" \
  -v transaction_type="$transaction_type" <<'SQL'
BEGIN;

WITH locked_wallet AS (
    SELECT id, balance
    FROM wallets
    WHERE user_id = :'user_id'::bigint
    FOR UPDATE
), updated_wallet AS (
    UPDATE wallets w
    SET balance = w.balance + :'amount'::numeric
    FROM locked_wallet lw
    WHERE w.id = lw.id
    RETURNING w.id, w.balance
), transaction_row AS (
    INSERT INTO wallet_transactions (
        wallet_id, type, amount, job_id, created_at, operation_key, balance_after
    )
    SELECT
        uw.id,
        :'transaction_type',
        :'amount'::numeric,
        NULL,
        CURRENT_TIMESTAMP,
        :'operation_key',
        uw.balance
    FROM updated_wallet uw
    RETURNING id, wallet_id
), funding_lot AS (
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
        tr.wallet_id,
        :'source_type',
        :'source_reference',
        :'amount'::numeric,
        :'amount'::numeric,
        CASE WHEN :'source_type' = 'EARNED_JOB' THEN TRUE ELSE FALSE END,
        CURRENT_TIMESTAMP
    FROM transaction_row tr
    RETURNING id, wallet_id
)
INSERT INTO wallet_funding_movements (
    wallet_transaction_id,
    funding_lot_id,
    amount,
    restores_movement_id,
    created_at
)
SELECT
    tr.id,
    fl.id,
    :'amount'::numeric,
    NULL,
    CURRENT_TIMESTAMP
FROM transaction_row tr
JOIN funding_lot fl ON fl.wallet_id = tr.wallet_id;

COMMIT;
SQL
