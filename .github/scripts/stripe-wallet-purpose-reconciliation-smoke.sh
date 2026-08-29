#!/usr/bin/env bash
set -euo pipefail
trap 'echo "Stripe wallet purpose reconciliation smoke failed at line $LINENO"' ERR

api='http://localhost:8080'

ADMIN_LOGIN=$(curl --fail --silent --show-error \
  -H 'Content-Type: application/json' \
  -d '{"email":"admin-ledger@example.com","password":"AdminLedgerPass123!"}' \
  "$api/users/login")
ADMIN_TOKEN=$(python3 -c 'import json,sys; print(json.load(sys.stdin)["accessToken"])' <<< "$ADMIN_LOGIN")

read -r ADMIN_ID WALLET_ID CATEGORY_ID <<< "$(docker compose exec -T db psql -U dofast -d dofast -At -F' ' -c \
  "SELECT u.id, w.id, (SELECT id FROM job_categories WHERE active=TRUE AND parent_id IS NOT NULL ORDER BY id LIMIT 1) FROM users u JOIN wallets w ON w.user_id=u.id WHERE u.email='admin-ledger@example.com';" | tr -d '\r')"

test -n "$ADMIN_ID"
test -n "$WALLET_ID"
test -n "$CATEGORY_ID"

PUBLICATION_ID=$(docker compose exec -T db psql -U dofast -d dofast -At -v ON_ERROR_STOP=1 <<SQL
WITH publication AS (
  INSERT INTO job_publications (
      user_id, request_key, payload_hash, request_payload, category_id,
      total_amount, wallet_reserved_amount, payment_amount, currency, status,
      stripe_payment_intent_id, created_at, updated_at, expires_at,
      payment_received_at, recovery_reason
  ) VALUES (
      $ADMIN_ID, 'smoke:publication-purpose', repeat('a',64), NULL, $CATEGORY_ID,
      2.00, 0.00, 2.00, 'PLN', 'PAYMENT_RECEIVED',
      'pi_smoke_publication_purpose', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP,
      CURRENT_TIMESTAMP, 'UNSPECIFIED'
  )
  RETURNING id
), wallet_update AS (
  UPDATE wallets
  SET balance = balance + 2.00
  WHERE id = $WALLET_ID
  RETURNING id, balance
), wallet_credit AS (
  INSERT INTO wallet_transactions (
      wallet_id, type, amount, job_id, created_at, operation_key, balance_after
  )
  SELECT wu.id, 'JOB_PUBLICATION_FUNDING', 2.00, NULL, CURRENT_TIMESTAMP,
         'stripe:intent:pi_smoke_publication_purpose', wu.balance
  FROM wallet_update wu
  RETURNING id, wallet_id
), funding_lot AS (
  INSERT INTO wallet_funding_lots (
      wallet_id, source_type, source_reference, original_amount,
      remaining_amount, withdrawable, created_at
  )
  SELECT wc.wallet_id, 'STRIPE_PAYMENT', 'pi_smoke_publication_purpose',
         2.00, 2.00, FALSE, CURRENT_TIMESTAMP
  FROM wallet_credit wc
  RETURNING id, wallet_id
), funding_movement AS (
  INSERT INTO wallet_funding_movements (
      wallet_transaction_id, funding_lot_id, amount, restores_movement_id, created_at
  )
  SELECT wc.id, fl.id, 2.00, NULL, CURRENT_TIMESTAMP
  FROM wallet_credit wc
  JOIN funding_lot fl ON fl.wallet_id = wc.wallet_id
  RETURNING id
), settlement AS (
  INSERT INTO payment_transactions (
      stripe_payment_intent_id, stripe_event_id, user_id, amount, currency,
      settlement_purpose, business_reference, processed_at
  )
  SELECT 'pi_smoke_publication_purpose', 'evt_smoke_publication_purpose', $ADMIN_ID,
         2.00, 'PLN', 'JOB_PUBLICATION', publication.id::text, CURRENT_TIMESTAMP
  FROM publication
  RETURNING business_reference
)
SELECT business_reference FROM settlement;
SQL
)
PUBLICATION_ID="${PUBLICATION_ID//[[:space:]]/}"
test -n "$PUBLICATION_ID"

FUNDING_SOURCE=$(docker compose exec -T db psql -U dofast -d dofast -tAc \
  "SELECT source_type || '|' || source_reference || '|' || remaining_amount::text || '|' || withdrawable FROM wallet_funding_lots WHERE wallet_id=$WALLET_ID AND source_reference='pi_smoke_publication_purpose';" | tr -d '[:space:]')
test "$FUNDING_SOURCE" = "STRIPE_PAYMENT|pi_smoke_publication_purpose|2.00|false"

HEALTHY=$(curl --fail --silent --show-error -H "Authorization: Bearer $ADMIN_TOKEN" "$api/admin/finance/reconciliation")
python3 -c 'import json,sys; d=json.load(sys.stdin); assert d["healthy"] is True; assert d["stripeLedgerMismatches"] == 0' <<< "$HEALTHY"

# Intentional settlement identity corruption: wallet funding remains valid while Stripe ledger linkage breaks.
docker compose exec -T db psql -U dofast -d dofast -v ON_ERROR_STOP=1 -c \
  "UPDATE wallet_transactions SET type='TOP_UP' WHERE operation_key='stripe:intent:pi_smoke_publication_purpose';"
BROKEN_TYPE=$(curl --fail --silent --show-error -H "Authorization: Bearer $ADMIN_TOKEN" "$api/admin/finance/reconciliation")
python3 -c 'import json,sys; d=json.load(sys.stdin); assert d["healthy"] is False; assert d["stripeLedgerMismatches"] == 1' <<< "$BROKEN_TYPE"

docker compose exec -T db psql -U dofast -d dofast -v ON_ERROR_STOP=1 -c \
  "UPDATE wallet_transactions SET type='JOB_PUBLICATION_FUNDING' WHERE operation_key='stripe:intent:pi_smoke_publication_purpose';"
RESTORED=$(curl --fail --silent --show-error -H "Authorization: Bearer $ADMIN_TOKEN" "$api/admin/finance/reconciliation")
python3 -c 'import json,sys; d=json.load(sys.stdin); assert d["healthy"] is True; assert d["stripeLedgerMismatches"] == 0' <<< "$RESTORED"

# Intentional orphan: remove provider settlement identity but leave the wallet credit and its provenance intact.
docker compose exec -T db psql -U dofast -d dofast -v ON_ERROR_STOP=1 -c \
  "DELETE FROM payment_transactions WHERE stripe_payment_intent_id='pi_smoke_publication_purpose';"
ORPHAN=$(curl --fail --silent --show-error -H "Authorization: Bearer $ADMIN_TOKEN" "$api/admin/finance/reconciliation")
python3 -c 'import json,sys; d=json.load(sys.stdin); assert d["healthy"] is False; assert d["stripeLedgerMismatches"] == 1' <<< "$ORPHAN"
