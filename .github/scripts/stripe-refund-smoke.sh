#!/usr/bin/env bash
set -euo pipefail
trap 'echo "Stripe refund smoke failed at line $LINENO"' ERR

api='http://localhost:8080'
webhook_secret='whsec_replace_me'
stripe_api_version='2026-07-29.dahlia'

REGISTER=$(curl --fail --silent --show-error \
  -H 'Content-Type: application/json' \
  -d '{"email":"refund-smoke@example.com","nickname":"refundSmoke","password":"RefundPass123!"}' \
  "$api/users")
USER_ID=$(python3 -c 'import json,sys; print(json.load(sys.stdin)["id"])' <<< "$REGISTER")
WALLET_ID=$(docker compose exec -T db psql -U dofast -d dofast -tAc \
  "SELECT id FROM wallets WHERE user_id=$USER_ID;" | tr -d '[:space:]')
test -n "$WALLET_ID"

# Two settled card payments seed 60 PLN as distinct, non-withdrawable Stripe funding lots.
bash .github/scripts/seed-wallet-funding.sh \
  "$USER_ID" 40.00 STRIPE_PAYMENT \
  'pi_refund_success_smoke' \
  'stripe:intent:pi_refund_success_smoke'
bash .github/scripts/seed-wallet-funding.sh \
  "$USER_ID" 20.00 STRIPE_PAYMENT \
  'pi_refund_failure_smoke' \
  'stripe:intent:pi_refund_failure_smoke'

# Model the two already-created refund reservations exactly like WalletService.debitFromStripePayment:
# request 1001 consumes 25 only from the first PaymentIntent lot, request 1002 consumes 15 only
# from the second one. The failure webhook must therefore restore request 1002 back into that lot.
docker compose exec -T db psql -U dofast -d dofast -v ON_ERROR_STOP=1 <<SQL
BEGIN;
SELECT id FROM wallets WHERE id = $WALLET_ID FOR UPDATE;

UPDATE wallet_funding_lots
SET remaining_amount = remaining_amount - 25.00
WHERE wallet_id = $WALLET_ID
  AND source_type = 'STRIPE_PAYMENT'
  AND source_reference = 'pi_refund_success_smoke'
  AND remaining_amount >= 25.00;

UPDATE wallet_funding_lots
SET remaining_amount = remaining_amount - 15.00
WHERE wallet_id = $WALLET_ID
  AND source_type = 'STRIPE_PAYMENT'
  AND source_reference = 'pi_refund_failure_smoke'
  AND remaining_amount >= 15.00;

UPDATE wallets SET balance = 20.00 WHERE id = $WALLET_ID;

WITH reserve_success AS (
  INSERT INTO wallet_transactions (wallet_id, type, amount, job_id, created_at, operation_key, balance_after)
  VALUES ($WALLET_ID, 'STRIPE_REFUND_RESERVE', -25.00, NULL, CURRENT_TIMESTAMP, 'stripe:refund:1001:reserve', 35.00)
  RETURNING id
)
INSERT INTO wallet_funding_movements (
  wallet_transaction_id, funding_lot_id, amount, restores_movement_id, created_at
)
SELECT rs.id, l.id, -25.00, NULL, CURRENT_TIMESTAMP
FROM reserve_success rs
JOIN wallet_funding_lots l
  ON l.wallet_id = $WALLET_ID
 AND l.source_type = 'STRIPE_PAYMENT'
 AND l.source_reference = 'pi_refund_success_smoke';

WITH reserve_failure AS (
  INSERT INTO wallet_transactions (wallet_id, type, amount, job_id, created_at, operation_key, balance_after)
  VALUES ($WALLET_ID, 'STRIPE_REFUND_RESERVE', -15.00, NULL, CURRENT_TIMESTAMP, 'stripe:refund:1002:reserve', 20.00)
  RETURNING id
)
INSERT INTO wallet_funding_movements (
  wallet_transaction_id, funding_lot_id, amount, restores_movement_id, created_at
)
SELECT rf.id, l.id, -15.00, NULL, CURRENT_TIMESTAMP
FROM reserve_failure rf
JOIN wallet_funding_lots l
  ON l.wallet_id = $WALLET_ID
 AND l.source_type = 'STRIPE_PAYMENT'
 AND l.source_reference = 'pi_refund_failure_smoke';

INSERT INTO payment_transactions (
  stripe_payment_intent_id, stripe_event_id, user_id, amount, currency,
  settlement_purpose, business_reference, processed_at
) VALUES
  ('pi_refund_success_smoke', 'evt_refund_original_success_1', $USER_ID, 40.00, 'PLN', 'TOP_UP', 'refund-smoke-1', CURRENT_TIMESTAMP),
  ('pi_refund_failure_smoke', 'evt_refund_original_success_2', $USER_ID, 20.00, 'PLN', 'TOP_UP', 'refund-smoke-2', CURRENT_TIMESTAMP);

INSERT INTO stripe_refund_requests (
  id, version, user_id, stripe_payment_intent_id, request_key, amount, currency, status,
  stripe_refund_id, stripe_status, failure_reason, attempt_count, next_attempt_at,
  provider_event_created_at, wallet_restored, created_at, updated_at, submitted_at, resolved_at
) VALUES
  (1001, 0, $USER_ID, 'pi_refund_success_smoke', 'refund-success-smoke', 25.00, 'PLN', 'PENDING',
   're_refund_success_smoke', 'pending', NULL, 1, NULL, NULL, FALSE,
   CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, NULL),
  (1002, 0, $USER_ID, 'pi_refund_failure_smoke', 'refund-failure-smoke', 15.00, 'PLN', 'PENDING',
   're_refund_failure_smoke', 'pending', NULL, 1, NULL, NULL, FALSE,
   CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, NULL);
COMMIT;
SQL

INITIAL_SOURCES=$(docker compose exec -T db psql -U dofast -d dofast -tAc \
  "SELECT string_agg(source_reference || ':' || remaining_amount::text || ':' || withdrawable, ',' ORDER BY source_reference) FROM wallet_funding_lots WHERE wallet_id=$WALLET_ID;" | tr -d '[:space:]')
test "$INITIAL_SOURCES" = "pi_refund_failure_smoke:5.00:false,pi_refund_success_smoke:15.00:false"

write_event() {
  local event_id="$1"
  local event_type="$2"
  local refund_id="$3"
  local payment_intent="$4"
  local amount="$5"
  local status="$6"
  local failure_reason="$7"
  local request_id="$8"
  local created="$9"
  local output="${10}"
  python3 - "$event_id" "$event_type" "$refund_id" "$payment_intent" "$amount" "$status" "$failure_reason" "$request_id" "$created" "$stripe_api_version" "$USER_ID" "$output" <<'PY'
import json,sys
(event_id,event_type,refund_id,payment_intent,amount,status,failure_reason,
 request_id,created,api_version,user_id,output)=sys.argv[1:]
refund={
  "id": refund_id,
  "object": "refund",
  "amount": int(amount),
  "currency": "pln",
  "payment_intent": payment_intent,
  "status": status,
  "metadata": {"dofastRefundId": request_id, "userId": user_id}
}
if failure_reason:
    refund["failure_reason"] = failure_reason
payload={
  "id": event_id,
  "object": "event",
  "api_version": api_version,
  "created": int(created),
  "data": {"object": refund},
  "livemode": False,
  "pending_webhooks": 1,
  "type": event_type
}
with open(output,'w',encoding='utf-8') as f:
    json.dump(payload,f,separators=(',',':'))
PY
}

post_signed_event() {
  local payload_file="$1"
  local response_file="$2"
  local timestamp signature status
  timestamp=$(date +%s)
  signature=$(python3 - "$webhook_secret" "$timestamp" "$payload_file" <<'PY'
import hashlib,hmac,sys
secret,timestamp,path=sys.argv[1:]
payload=open(path,'rb').read()
print(hmac.new(secret.encode(), timestamp.encode()+b'.'+payload, hashlib.sha256).hexdigest())
PY
)
  status=$(curl --silent --show-error --output "$response_file" --write-out '%{http_code}' \
    -H 'Content-Type: application/json' \
    -H "Stripe-Signature: t=$timestamp,v1=$signature" \
    --data-binary "@$payload_file" \
    "$api/webhooks/stripe")
  test "$status" = "200"
}

NOW=$(date +%s)
write_event 'evt_refund_succeeded' 'refund.updated' 're_refund_success_smoke' \
  'pi_refund_success_smoke' 2500 'succeeded' '' 1001 "$NOW" /tmp/refund-success.json
post_signed_event /tmp/refund-success.json /tmp/refund-success-response.txt
grep -qx 'ok' /tmp/refund-success-response.txt

SUCCESS_STATE=$(docker compose exec -T db psql -U dofast -d dofast -tAc \
  "SELECT status || '|' || wallet_restored FROM stripe_refund_requests WHERE id=1001;" | tr -d '[:space:]')
test "$SUCCESS_STATE" = "SUCCEEDED|false"
BALANCE_AFTER_SUCCESS=$(docker compose exec -T db psql -U dofast -d dofast -tAc \
  "SELECT balance FROM wallets WHERE id=$WALLET_ID;" | tr -d '[:space:]')
test "$BALANCE_AFTER_SUCCESS" = "20.00"
SUCCESS_SOURCE_REMAINING=$(docker compose exec -T db psql -U dofast -d dofast -tAc \
  "SELECT remaining_amount::text FROM wallet_funding_lots WHERE wallet_id=$WALLET_ID AND source_reference='pi_refund_success_smoke';" | tr -d '[:space:]')
test "$SUCCESS_SOURCE_REMAINING" = "15.00"

post_signed_event /tmp/refund-success.json /tmp/refund-success-replay.txt
grep -qx 'already processed' /tmp/refund-success-replay.txt

write_event 'evt_refund_failed' 'refund.failed' 're_refund_failure_smoke' \
  'pi_refund_failure_smoke' 1500 'failed' 'declined' 1002 "$((NOW+10))" /tmp/refund-failed.json
post_signed_event /tmp/refund-failed.json /tmp/refund-failed-response.txt
grep -qx 'ok' /tmp/refund-failed-response.txt

FAILED_STATE=$(docker compose exec -T db psql -U dofast -d dofast -tAc \
  "SELECT status || '|' || wallet_restored || '|' || COALESCE(failure_reason,'') FROM stripe_refund_requests WHERE id=1002;" | tr -d '[:space:]')
test "$FAILED_STATE" = "FAILED|true|declined"
BALANCE_AFTER_FAILURE=$(docker compose exec -T db psql -U dofast -d dofast -tAc \
  "SELECT balance FROM wallets WHERE id=$WALLET_ID;" | tr -d '[:space:]')
test "$BALANCE_AFTER_FAILURE" = "35.00"
RESTORE_COUNT=$(docker compose exec -T db psql -U dofast -d dofast -tAc \
  "SELECT COUNT(*) FROM wallet_transactions WHERE wallet_id=$WALLET_ID AND type='STRIPE_REFUND_RESTORE';" | tr -d '[:space:]')
test "$RESTORE_COUNT" = "1"
FAILURE_SOURCE_REMAINING=$(docker compose exec -T db psql -U dofast -d dofast -tAc \
  "SELECT remaining_amount::text FROM wallet_funding_lots WHERE wallet_id=$WALLET_ID AND source_reference='pi_refund_failure_smoke';" | tr -d '[:space:]')
test "$FAILURE_SOURCE_REMAINING" = "20.00"
RESTORE_MOVEMENT=$(docker compose exec -T db psql -U dofast -d dofast -tAc \
  "SELECT m.amount::text || ':' || (m.restores_movement_id IS NOT NULL) FROM wallet_funding_movements m JOIN wallet_transactions wt ON wt.id=m.wallet_transaction_id WHERE wt.wallet_id=$WALLET_ID AND wt.type='STRIPE_REFUND_RESTORE';" | tr -d '[:space:]')
test "$RESTORE_MOVEMENT" = "15.00:true"

post_signed_event /tmp/refund-failed.json /tmp/refund-failed-replay.txt
grep -qx 'already processed' /tmp/refund-failed-replay.txt
FINAL_BALANCE=$(docker compose exec -T db psql -U dofast -d dofast -tAc \
  "SELECT balance FROM wallets WHERE id=$WALLET_ID;" | tr -d '[:space:]')
test "$FINAL_BALANCE" = "35.00"
FINAL_RESTORE_COUNT=$(docker compose exec -T db psql -U dofast -d dofast -tAc \
  "SELECT COUNT(*) FROM wallet_transactions WHERE wallet_id=$WALLET_ID AND type='STRIPE_REFUND_RESTORE';" | tr -d '[:space:]')
test "$FINAL_RESTORE_COUNT" = "1"
EVENT_COUNT=$(docker compose exec -T db psql -U dofast -d dofast -tAc \
  "SELECT COUNT(*) FROM stripe_refund_events WHERE refund_request_id IN (1001,1002);" | tr -d '[:space:]')
test "$EVENT_COUNT" = "2"
FINAL_COVERAGE=$(docker compose exec -T db psql -U dofast -d dofast -tAc \
  "SELECT w.balance = COALESCE(sum(l.remaining_amount),0) FROM wallets w LEFT JOIN wallet_funding_lots l ON l.wallet_id=w.id WHERE w.id=$WALLET_ID GROUP BY w.id;" | tr -d '[:space:]')
test "$FINAL_COVERAGE" = "t"

FINAL_SOURCES=$(docker compose exec -T db psql -U dofast -d dofast -tAc \
  "SELECT string_agg(source_reference || ':' || remaining_amount::text || ':' || withdrawable, ',' ORDER BY source_reference) FROM wallet_funding_lots WHERE wallet_id=$WALLET_ID;" | tr -d '[:space:]')
test "$FINAL_SOURCES" = "pi_refund_failure_smoke:20.00:false,pi_refund_success_smoke:15.00:false"

echo 'Signed Stripe original-method refund success, exact-source failure restoration and replay handling: OK'
