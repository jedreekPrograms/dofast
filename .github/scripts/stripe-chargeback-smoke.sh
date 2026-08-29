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

# Model a settled 50 PLN top-up of which the user has already spent 40 PLN. The current wallet
# therefore has only 10 PLN available when Stripe removes 30 PLN because of a dispute.
docker compose exec -T db psql -U dofast -d dofast -v ON_ERROR_STOP=1 <<SQL
BEGIN;
UPDATE wallets SET balance = 10.00 WHERE id = $WALLET_ID;
INSERT INTO wallet_transactions (wallet_id, type, amount, job_id, created_at, operation_key, balance_after)
VALUES
  ($WALLET_ID, 'TOP_UP', 50.00, NULL, CURRENT_TIMESTAMP, 'smoke:chargeback:original-credit', 50.00),
  ($WALLET_ID, 'WITHDRAW', -40.00, NULL, CURRENT_TIMESTAMP, 'smoke:chargeback:already-spent', 10.00);
INSERT INTO payment_transactions (
  stripe_payment_intent_id, stripe_event_id, user_id, amount, currency,
  settlement_purpose, business_reference, processed_at
) VALUES (
  'pi_chargeback_smoke', 'evt_chargeback_original_success', $USER_ID, 50.00, 'PLN',
  'TOP_UP', 'chargeback-smoke-topup', CURRENT_TIMESTAMP
);
COMMIT;
SQL

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

# A later incoming credit remains non-negative and is automatically swept by the recovery worker.
docker compose exec -T db psql -U dofast -d dofast -v ON_ERROR_STOP=1 <<SQL
BEGIN;
UPDATE wallets SET balance = 15.00 WHERE id = $WALLET_ID;
INSERT INTO wallet_transactions (wallet_id, type, amount, job_id, created_at, operation_key, balance_after)
VALUES ($WALLET_ID, 'REFUND', 15.00, NULL, CURRENT_TIMESTAMP, 'smoke:chargeback:future-credit', 15.00);
COMMIT;
SQL

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

write_event 'evt_chargeback_reinstated' 'charge.dispute.funds_reinstated' 'won' /tmp/chargeback-reinstated.json
post_signed_event /tmp/chargeback-reinstated.json /tmp/chargeback-reinstated-response.txt
grep -qx 'ok' /tmp/chargeback-reinstated-response.txt

FINAL_STATE=$(docker compose exec -T db psql -U dofast -d dofast -tAc \
  "SELECT funds_withdrawn || '|' || funds_reinstated || '|' || wallet_recovered_amount || '|' || wallet_returned_amount || '|' || outstanding_amount FROM stripe_payment_disputes WHERE stripe_dispute_id='dp_chargeback_smoke';" | tr -d '[:space:]')
test "$FINAL_STATE" = "true|true|25.00|25.00|0.00"
FINAL_BALANCE=$(docker compose exec -T db psql -U dofast -d dofast -tAc \
  "SELECT balance FROM wallets WHERE id=$WALLET_ID;" | tr -d '[:space:]')
test "$FINAL_BALANCE" = "25.00"

# Replaying the exact Stripe event must not create another wallet credit.
post_signed_event /tmp/chargeback-reinstated.json /tmp/chargeback-replay-response.txt
grep -qx 'already processed' /tmp/chargeback-replay-response.txt
REINSTATEMENT_COUNT=$(docker compose exec -T db psql -U dofast -d dofast -tAc \
  "SELECT COUNT(*) FROM wallet_transactions WHERE wallet_id=$WALLET_ID AND type='CHARGEBACK_REINSTATEMENT';" | tr -d '[:space:]')
test "$REINSTATEMENT_COUNT" = "1"
EVENT_COUNT=$(docker compose exec -T db psql -U dofast -d dofast -tAc \
  "SELECT COUNT(*) FROM stripe_payment_dispute_events WHERE stripe_dispute_id='dp_chargeback_smoke';" | tr -d '[:space:]')
test "$EVENT_COUNT" = "2"

echo 'Signed Stripe chargeback withdrawal, recovery, reinstatement and replay handling: OK'
