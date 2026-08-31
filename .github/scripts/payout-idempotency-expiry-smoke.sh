#!/usr/bin/env bash
set -euo pipefail
trap 'echo "Payout idempotency expiry smoke failed at line $LINENO"' ERR

USER_ID=$(docker compose exec -T db psql -U dofast -d dofast -tAc \
  "SELECT id FROM users WHERE email='payout-worker@example.com';" | tr -d '[:space:]')
test -n "$USER_ID"

PAYOUT_ID=$(docker compose exec -T db psql -U dofast -d dofast -tAc "
INSERT INTO payout_requests (
    user_id,
    request_key,
    amount,
    currency,
    status,
    provider_code,
    attempt_count,
    requested_at,
    next_attempt_at,
    processing_started_at
) VALUES (
    $USER_ID,
    'payout-idempotency-expiry-smoke',
    3.00,
    'PLN',
    'PROCESSING',
    'stripe-connect',
    1,
    CURRENT_TIMESTAMP - INTERVAL '26 hours',
    CURRENT_TIMESTAMP - INTERVAL '25 hours',
    CURRENT_TIMESTAMP - INTERVAL '25 hours'
)
RETURNING id;
" | tr -d '[:space:]')
test -n "$PAYOUT_ID"

STATE=''
for attempt in {1..30}; do
  STATE=$(docker compose exec -T db psql -U dofast -d dofast -tAc \
    "SELECT status || '|' || COALESCE(failure_code, '') || '|' || attempt_count || '|' || COALESCE(provider_reference, '') || '|' || COALESCE(provider_transfer_reference, '') FROM payout_requests WHERE id=$PAYOUT_ID;" | tr -d '[:space:]')
  if test "$STATE" = 'REVIEW_REQUIRED|STRIPE_IDEMPOTENCY_WINDOW_EXPIRED|1||'; then
    break
  fi
  sleep 1
done

test "$STATE" = 'REVIEW_REQUIRED|STRIPE_IDEMPOTENCY_WINDOW_EXPIRED|1||'

RESTORE_COUNT=$(docker compose exec -T db psql -U dofast -d dofast -tAc \
  "SELECT count(*) FROM wallet_transactions WHERE operation_key='payout:${PAYOUT_ID}:restore';" | tr -d '[:space:]')
test "$RESTORE_COUNT" = '0'

REVIEW_EVENT_COUNT=$(docker compose exec -T db psql -U dofast -d dofast -tAc \
  "SELECT count(*) FROM payout_events WHERE payout_id=$PAYOUT_ID AND event_type='REVIEW_REQUIRED';" | tr -d '[:space:]')
test "$REVIEW_EVENT_COUNT" = '1'

echo 'Expired ambiguous Stripe Connect dispatch is quarantined without retry or wallet restore: OK'
