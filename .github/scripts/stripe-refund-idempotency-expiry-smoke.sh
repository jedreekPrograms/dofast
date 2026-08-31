#!/usr/bin/env bash
set -euo pipefail
trap 'echo "Stripe refund idempotency expiry smoke failed at line $LINENO"' ERR

api='http://localhost:8080'
email='refund-idempotency-expiry-smoke@example.com'
intent='pi_refund_idempotency_expiry_smoke'

REGISTERED=$(curl --fail --silent --show-error \
  -H 'Content-Type: application/json' \
  -d "{\"email\":\"$email\",\"nickname\":\"refundExpirySmoke\",\"password\":\"RefundExpiryPass123!\"}" \
  "$api/users")
USER_ID=$(python3 -c 'import json,sys; print(json.load(sys.stdin)["id"])' <<< "$REGISTERED")
test -n "$USER_ID"

docker compose exec -T db psql -U dofast -d dofast -v ON_ERROR_STOP=1 <<SQL
INSERT INTO payment_transactions (
    stripe_payment_intent_id,
    user_id,
    amount,
    stripe_event_id,
    currency,
    processed_at,
    settlement_purpose,
    business_reference
) VALUES (
    '$intent',
    $USER_ID,
    100.00,
    'evt_refund_idempotency_expiry_smoke',
    'PLN',
    CURRENT_TIMESTAMP,
    'TOP_UP',
    'refund-idempotency-expiry-smoke'
);

INSERT INTO stripe_refund_requests (
    user_id,
    stripe_payment_intent_id,
    request_key,
    amount,
    currency,
    status,
    attempt_count,
    next_attempt_at,
    wallet_restored,
    created_at,
    updated_at
) VALUES (
    $USER_ID,
    '$intent',
    'refund-idempotency-expiry-smoke',
    25.00,
    'PLN',
    'DISPATCHING',
    1,
    NULL,
    FALSE,
    CURRENT_TIMESTAMP - INTERVAL '26 hours',
    CURRENT_TIMESTAMP - INTERVAL '25 hours'
);
SQL

for attempt in {1..12}; do
  STATE=$(docker compose exec -T db psql -U dofast -d dofast -tAc \
    "SELECT status || '|' || COALESCE(failure_reason, '') || '|' || wallet_restored || '|' || attempt_count || '|' || COALESCE(stripe_refund_id, '') FROM stripe_refund_requests WHERE request_key='refund-idempotency-expiry-smoke';" | tr -d '[:space:]')
  if test "$STATE" = 'REVIEW_REQUIRED|provider_idempotency_window_expired|false|1|'; then
    break
  fi
  sleep 1
done

test "$STATE" = 'REVIEW_REQUIRED|provider_idempotency_window_expired|false|1|'

RESTORE_COUNT=$(docker compose exec -T db psql -U dofast -d dofast -tAc \
  "SELECT count(*) FROM wallet_transactions wt JOIN wallets w ON w.id=wt.wallet_id WHERE w.user_id=$USER_ID AND wt.type='STRIPE_REFUND_RESTORE';" | tr -d '[:space:]')
test "$RESTORE_COUNT" = '0'

echo 'Expired ambiguous Stripe refund is quarantined without provider retry or wallet restore: OK'
