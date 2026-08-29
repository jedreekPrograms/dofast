#!/usr/bin/env bash
set -euo pipefail
trap 'echo "Stripe chargeback smoke failed at line $LINENO"' ERR

api='http://localhost:8080'
webhook_secret='whsec_replace_me'
stripe_api_version='2026-07-29.dahlia'

REGISTER=$(curl --fail --silent --show-error \
  -H 'Content-Type: application/json' \
  -d '{"email":"chargeback-smoke@example.com","nickname":"chargebackSmoke","password":"ChargebackPass123!"}' \
  "$api/users")
USER_ID=$(python3 -c 'import json,sys; print(json.load(sys.stdin)["id"])' <<< "$REGISTER")
WALLET_ID=$(docker compose exec -T db psql -U dofast -d dofast -tAc \
  "SELECT id FROM wallets WHERE user_id=$USER_ID;" | tr -d '[:space:]')
test -n "$WALLET_ID"

# Model a settled 50 PLN card top-up. Forty PLN was already spent inside doFast, so only ten
# remains in the exact Stripe PaymentIntent lot when Stripe later withdraws 30 PLN for a dispute.
bash .github/scripts/seed-wallet-funding.sh \
  "$USER_ID" 50.00 STRIPE_PAYMENT \
  'pi_chargeback_smoke' \
  'stripe:intent:pi_chargeback_smoke'

docker compose exec -T db psql -U dofast -d dofast -v ON_ERROR_STOP=1 <<SQL
BEGIN;
SELECT id FROM wallets WHERE id=$WALLET_ID FOR UPDATE;
UPDATE wallet_funding_lots
SET remaining_amount = remaining_amount - 40.00
WHERE wallet_id=$WALLET_ID
  AND source_type='STRIPE_PAYMENT'
  AND source_reference='pi_chargeback_smoke'
  AND remaining_amount >= 40.00;
UPDATE wallets SET balance=10.00 WHERE id=$WALLET_ID;
WITH spent AS (
  INSERT INTO wallet_transactions (wallet_id, type, amount, job_id, created_at, operation_key, balance_after)
  VALUES ($WALLET_ID, 'ESCROW_LOCK', -40.00, NULL, CURRENT_TIMESTAMP, 'smoke:chargeback:already-spent', 10.00)
  RETURNING id
)
INSERT INTO wallet_funding_movements (
  wallet_transaction_id, funding_lot_id, amount, restores_movement_id, created_at
)
SELECT spent.id, lot.id, -40.00, NULL, CURRENT_TIMESTAMP
FROM spent
JOIN wallet_funding_lots lot
  ON lot.wallet_id=$WALLET_ID
 AND lot.source_type='STRIPE_PAYMENT'
 AND lot.source_reference='pi_chargeback_smoke';

INSERT INTO payment_transactions (
  stripe_payment_intent_id, stripe_event_id, user_id, amount, currency,
  settlement_purpose, business_reference, processed_at
) VALUES (
  'pi_chargeback_smoke', 'evt_chargeback_original_success', $USER_ID, 50.00, 'PLN',
  'TOP_UP', 'chargeback-smoke-topup', CURRENT_TIMESTAMP
);
COMMIT;
SQL

INITIAL_SOURCE=$(docker compose exec -T db psql -U dofast -d dofast -tAc \
  "SELECT source_type || '|' || source_reference || '|' || remaining_amount::text || '|' || withdrawable FROM wallet_funding_lots WHERE wallet_id=$WALLET_ID;" | tr -d '[:space:]')
test "$INITIAL_SOURCE" = "STRIPE_PAYMENT|pi_chargeback_smoke|10.00|false"

write_event() {
  local event_id="$1"
  local event_type="$2"
  local dispute_status="$3"
  local output="$4"
  python3 - "$event_id" "$event_type" "$dispute_status" "$stripe_api_version" "$output" <<'PY'
import json,sys,time
id_, typ, status, api_version, output = sys.argv[1:]
payload = {
  "id": id_,
  "object": "event",
  "api_version": api_version,
  "created": int(time.time()),
  "data": {"object": {
    "id": "dp_chargeback_smoke",
    "object": "dispute",
    "amount": 3000,
    "currency": "pln",
    "charge": "ch_chargeback_smoke",
    "payment_intent": "pi_chargeback_smoke",
    "reason": "fraudulent",
    "status": status
  }},
  "livemode": False,
  "pending_webhooks": 1,
  "type": typ
}
with open(output, 'w', encoding='utf-8') as f:
    json.dump(payload, f, separators=(',', ':'))
PY
}

post_signed_event() {
  local payload_file="$1"
  local response_file="$2"
  local timestamp signature status
  timestamp=$(date +%s)
  signature=$(python3 - "$webhook_secret" "$timestamp" "$payload_file" <<'PY'
import hashlib,hmac,sys
secret, timestamp, path = sys.argv[1:]
payload=open(path,'rb').read()
signed=timestamp.encode()+b'.'+payload
print(hmac.new(secret.encode(), signed, hashlib.sha256).hexdigest())
PY
)
  status=$(curl --silent --show-error --output "$response_file" --write-out '%{http_code}' \
    -H 'Content-Type: application/json' \
    -H "Stripe-Signature: t=$timestamp,v1=$signature" \
    --data-binary "@$payload_file" \
    "$api/webhooks/stripe")
  test "$status" = "200"
}

write_event 'evt_chargeback_withdrawn' 'charge.dispute.funds_withdrawn' 'needs_response' /tmp/chargeback-withdrawn.json
post_signed_event /tmp/chargeback-withdrawn.json /tmp/chargeback-withdrawn-response.txt
grep -qx 'ok' /tmp/chargeback-withdrawn-response.txt

INITIAL_STATE=$(docker compose exec -T db psql -U dofast -d dofast -tAc \
  "SELECT funds_withdrawn || '|' || funds_reinstated || '|' || wallet_recovered_amount || '|' || outstanding_amount FROM stripe_payment_disputes WHERE stripe_dispute_id='dp_chargeback_smoke';" | tr -d '[:space:]')
test "$INITIAL_STATE" = "true|false|10.00|20.00"
INITIAL_BALANCE=$(docker compose exec -T db psql -U dofast -d dofast -tAc \
  "SELECT balance FROM wallets WHERE id=$WALLET_ID;" | tr -d '[:space:]')
test "$INITIAL_BALANCE" = "0.00"
STRIPE_AFTER_RECOVERY=$(docker compose exec -T db psql -U dofast -d dofast -tAc \
  "SELECT remaining_amount::text FROM wallet_funding_lots WHERE wallet_id=$WALLET_ID AND source_reference='pi_chargeback_smoke';" | tr -d '[:space:]')
test "$STRIPE_AFTER_RECOVERY" = "0.00"

# A later real doFast earning remains non-negative and is automatically swept by the recovery worker.
bash .github/scripts/seed-wallet-funding.sh \
  "$USER_ID" 15.00 EARNED_JOB \
  'smoke:chargeback:future-earned' \
  'smoke:chargeback:future-earned' \
  ESCROW_RELEASE

RECOVERED=''
for attempt in {1..40}; do
  RECOVERED=$(docker compose exec -T db psql -U dofast -d dofast -tAc \
    "SELECT wallet_recovered_amount || '|' || outstanding_amount || '|' || (SELECT balance FROM wallets WHERE id=$WALLET_ID) FROM stripe_payment_disputes WHERE stripe_dispute_id='dp_chargeback_smoke';" | tr -d '[:space:]')
  if test "$RECOVERED" = "25.00|5.00|0.00"; then
    break
  fi
  sleep 1
done
test "$RECOVERED" = "25.00|5.00|0.00"

RECOVERY_COUNT=$(docker compose exec -T db psql -U dofast -d dofast -tAc \
  "SELECT COUNT(*) FROM wallet_transactions WHERE wallet_id=$WALLET_ID AND type='CHARGEBACK_RECOVERY';" | tr -d '[:space:]')
test "$RECOVERY_COUNT" = "2"
EARNED_AFTER_RECOVERY=$(docker compose exec -T db psql -U dofast -d dofast -tAc \
  "SELECT remaining_amount::text FROM wallet_funding_lots WHERE wallet_id=$WALLET_ID AND source_type='EARNED_JOB';" | tr -d '[:space:]')
test "$EARNED_AFTER_RECOVERY" = "0.00"

write_event 'evt_chargeback_reinstated' 'charge.dispute.funds_reinstated' 'won' /tmp/chargeback-reinstated.json
post_signed_event /tmp/chargeback-reinstated.json /tmp/chargeback-reinstated-response.txt
grep -qx 'ok' /tmp/chargeback-reinstated-response.txt

FINAL_STATE=$(docker compose exec -T db psql -U dofast -d dofast -tAc \
  "SELECT funds_withdrawn || '|' || funds_reinstated || '|' || wallet_recovered_amount || '|' || wallet_returned_amount || '|' || outstanding_amount FROM stripe_payment_disputes WHERE stripe_dispute_id='dp_chargeback_smoke';" | tr -d '[:space:]')
test "$FINAL_STATE" = "true|true|25.00|25.00|0.00"
FINAL_BALANCE=$(docker compose exec -T db psql -U dofast -d dofast -tAc \
  "SELECT balance FROM wallets WHERE id=$WALLET_ID;" | tr -d '[:space:]')
test "$FINAL_BALANCE" = "25.00"
FINAL_SOURCES=$(docker compose exec -T db psql -U dofast -d dofast -tAc \
  "SELECT string_agg(source_type || ':' || remaining_amount::text || ':' || withdrawable, ',' ORDER BY source_type) FROM wallet_funding_lots WHERE wallet_id=$WALLET_ID;" | tr -d '[:space:]')
test "$FINAL_SOURCES" = "EARNED_JOB:15.00:true,STRIPE_PAYMENT:10.00:false"
REINSTATEMENT_MOVEMENTS=$(docker compose exec -T db psql -U dofast -d dofast -tAc \
  "SELECT string_agg(m.amount::text || ':' || l.source_type, ',' ORDER BY l.source_type) FROM wallet_funding_movements m JOIN wallet_transactions wt ON wt.id=m.wallet_transaction_id JOIN wallet_funding_lots l ON l.id=m.funding_lot_id WHERE wt.wallet_id=$WALLET_ID AND wt.type='CHARGEBACK_REINSTATEMENT';" | tr -d '[:space:]')
test "$REINSTATEMENT_MOVEMENTS" = "15.00:EARNED_JOB,10.00:STRIPE_PAYMENT"

# Replaying the exact Stripe event must not create another wallet credit.
post_signed_event /tmp/chargeback-reinstated.json /tmp/chargeback-replay-response.txt
grep -qx 'already processed' /tmp/chargeback-replay-response.txt
REINSTATEMENT_COUNT=$(docker compose exec -T db psql -U dofast -d dofast -tAc \
  "SELECT COUNT(*) FROM wallet_transactions WHERE wallet_id=$WALLET_ID AND type='CHARGEBACK_REINSTATEMENT';" | tr -d '[:space:]')
test "$REINSTATEMENT_COUNT" = "1"
EVENT_COUNT=$(docker compose exec -T db psql -U dofast -d dofast -tAc \
  "SELECT COUNT(*) FROM stripe_payment_dispute_events WHERE stripe_dispute_id='dp_chargeback_smoke';" | tr -d '[:space:]')
test "$EVENT_COUNT" = "2"
FINAL_COVERAGE=$(docker compose exec -T db psql -U dofast -d dofast -tAc \
  "SELECT w.balance = COALESCE(sum(l.remaining_amount),0) FROM wallets w LEFT JOIN wallet_funding_lots l ON l.wallet_id=w.id WHERE w.id=$WALLET_ID GROUP BY w.id;" | tr -d '[:space:]')
test "$FINAL_COVERAGE" = "t"

echo 'Signed Stripe chargeback withdrawal, mixed-source recovery, exact reinstatement and replay handling: OK'
