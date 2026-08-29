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

# Two settled card payments seed 60 PLN. We then reserve 25 PLN for a successful refund
# and 15 PLN for a second refund that Stripe will fail. Only the failed refund may return
# its reservation to the wallet.
docker compose exec -T db psql -U dofast -d dofast -v ON_ERROR_STOP=1 <<SQL
BEGIN;
UPDATE wallets SET balance = 20.00 WHERE id = $WALLET_ID;
INSERT INTO wallet_transactions (wallet_id, type, amount, job_id, created_at, operation_key, balance_after)
VALUES
  ($WALLET_ID, 'TOP_UP', 40.00, NULL, CURRENT_TIMESTAMP, 'smoke:refund:credit-1', 40.00),
  ($WALLET_ID, 'TOP_UP', 20.00, NULL, CURRENT_TIMESTAMP, 'smoke:refund:credit-2', 60.00),
  ($WALLET_ID, 'STRIPE_REFUND_RESERVE', -25.00, NULL, CURRENT_TIMESTAMP, 'stripe:refund:1001:reserve', 35.00),
  ($WALLET_ID, 'STRIPE_REFUND_RESERVE', -15.00, NULL, CURRENT_TIMESTAMP, 'stripe:refund:1002:reserve', 20.00);
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

echo 'Signed Stripe original-method refund success, failure restoration and replay handling: OK'
