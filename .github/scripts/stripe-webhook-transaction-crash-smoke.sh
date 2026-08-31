#!/usr/bin/env bash
set -euo pipefail
trap 'echo "Stripe webhook transaction crash smoke failed at line $LINENO"' ERR

api='http://localhost:8080'
webhook_secret='whsec_replace_me'
payment_intent_id='pi_webhook_tx_crash'
event_id='evt_webhook_tx_crash'
operation_key="stripe:intent:${payment_intent_id}"
trigger_name='smoke_fail_webhook_wallet_insert'
function_name='smoke_fail_webhook_wallet_insert_fn'

cleanup_failpoint() {
  docker compose exec -T db psql -U dofast -d dofast -v ON_ERROR_STOP=1 >/dev/null 2>&1 <<SQL || true
DROP TRIGGER IF EXISTS ${trigger_name} ON wallet_transactions;
DROP FUNCTION IF EXISTS ${function_name}();
SQL
}
trap 'cleanup_failpoint' EXIT

REGISTERED=$(curl --fail --silent --show-error \
  -H 'Content-Type: application/json' \
  -d '{"email":"webhook-crash@example.com","nickname":"webhookCrash","password":"WebhookCrashPass123!"}' \
  "$api/users")
USER_ID=$(python3 -c 'import json,sys; print(json.load(sys.stdin)["id"])' <<< "$REGISTERED")
test -n "$USER_ID"

WALLET_ID=$(docker compose exec -T db psql -U dofast -d dofast -tAc \
  "SELECT id FROM wallets WHERE user_id=$USER_ID;" | tr -d '[:space:]')
test -n "$WALLET_ID"

INITIAL_BALANCE=$(docker compose exec -T db psql -U dofast -d dofast -tAc \
  "SELECT balance FROM wallets WHERE id=$WALLET_ID;" | tr -d '[:space:]')
test "$INITIAL_BALANCE" = '0.00'

TIMESTAMP=$(date +%s)
PAYLOAD=$(python3 - "$USER_ID" "$TIMESTAMP" <<'PY'
import json
import sys

user_id = sys.argv[1]
created = int(sys.argv[2])
event = {
    "id": "evt_webhook_tx_crash",
    "object": "event",
    "created": created,
    "data": {
        "object": {
            "id": "pi_webhook_tx_crash",
            "object": "payment_intent",
            "amount": 1234,
            "currency": "pln",
            "livemode": False,
            "metadata": {
                "userId": user_id,
                "purpose": "TOP_UP",
                "topUpRequestId": "webhook-tx-crash"
            },
            "status": "succeeded"
        }
    },
    "livemode": False,
    "pending_webhooks": 1,
    "type": "payment_intent.succeeded"
}
print(json.dumps(event, separators=(",", ":")))
PY
)

SIGNATURE=$(python3 - "$webhook_secret" "$TIMESTAMP" "$PAYLOAD" <<'PY'
import hashlib
import hmac
import sys

secret, timestamp, payload = sys.argv[1:]
message = f"{timestamp}.{payload}".encode()
print(hmac.new(secret.encode(), message, hashlib.sha256).hexdigest())
PY
)
SIGNATURE_HEADER="t=${TIMESTAMP},v1=${SIGNATURE}"

# Fail exactly when the wallet ledger insert is attempted. StripePaymentService claims the
# PaymentIntent first, so this simulates a process/database failure after claim but before the
# surrounding transaction can commit the wallet credit.
docker compose exec -T db psql -U dofast -d dofast -v ON_ERROR_STOP=1 >/dev/null <<SQL
CREATE OR REPLACE FUNCTION ${function_name}()
RETURNS trigger
LANGUAGE plpgsql
AS \$\$
BEGIN
  IF NEW.operation_key = '${operation_key}' THEN
    RAISE EXCEPTION 'intentional Stripe webhook transaction crash smoke';
  END IF;
  RETURN NEW;
END;
\$\$;

CREATE TRIGGER ${trigger_name}
BEFORE INSERT ON wallet_transactions
FOR EACH ROW
EXECUTE FUNCTION ${function_name}();
SQL

FIRST_STATUS=$(curl --silent --show-error \
  --output /tmp/webhook-crash-first.txt \
  --write-out '%{http_code}' \
  -H 'Content-Type: application/json' \
  -H "Stripe-Signature: ${SIGNATURE_HEADER}" \
  --data-binary "$PAYLOAD" \
  "$api/webhooks/stripe")
test "$FIRST_STATUS" = '500'
grep -q 'processing failed' /tmp/webhook-crash-first.txt

CLAIM_AFTER_FAILURE=$(docker compose exec -T db psql -U dofast -d dofast -tAc \
  "SELECT count(*) FROM payment_transactions WHERE stripe_payment_intent_id='${payment_intent_id}' OR stripe_event_id='${event_id}';" | tr -d '[:space:]')
LEDGER_AFTER_FAILURE=$(docker compose exec -T db psql -U dofast -d dofast -tAc \
  "SELECT count(*) FROM wallet_transactions WHERE operation_key='${operation_key}';" | tr -d '[:space:]')
BALANCE_AFTER_FAILURE=$(docker compose exec -T db psql -U dofast -d dofast -tAc \
  "SELECT balance FROM wallets WHERE id=$WALLET_ID;" | tr -d '[:space:]')
FUNDING_AFTER_FAILURE=$(docker compose exec -T db psql -U dofast -d dofast -tAc \
  "SELECT count(*) FROM wallet_funding_lots WHERE wallet_id=$WALLET_ID;" | tr -d '[:space:]')

test "$CLAIM_AFTER_FAILURE" = '0'
test "$LEDGER_AFTER_FAILURE" = '0'
test "$BALANCE_AFTER_FAILURE" = '0.00'
test "$FUNDING_AFTER_FAILURE" = '0'

cleanup_failpoint
trap - EXIT

SECOND_STATUS=$(curl --silent --show-error \
  --output /tmp/webhook-crash-second.txt \
  --write-out '%{http_code}' \
  -H 'Content-Type: application/json' \
  -H "Stripe-Signature: ${SIGNATURE_HEADER}" \
  --data-binary "$PAYLOAD" \
  "$api/webhooks/stripe")
test "$SECOND_STATUS" = '200'
grep -q '^ok$' /tmp/webhook-crash-second.txt

PAYMENT_STATE=$(docker compose exec -T db psql -U dofast -d dofast -tAc \
  "SELECT stripe_payment_intent_id || '|' || stripe_event_id || '|' || user_id || '|' || amount::text || '|' || currency || '|' || settlement_purpose || '|' || business_reference FROM payment_transactions WHERE stripe_payment_intent_id='${payment_intent_id}';" | tr -d '[:space:]')
test "$PAYMENT_STATE" = "${payment_intent_id}|${event_id}|${USER_ID}|12.34|PLN|TOP_UP|webhook-tx-crash"

LEDGER_STATE=$(docker compose exec -T db psql -U dofast -d dofast -tAc \
  "SELECT type || '|' || amount::text || '|' || balance_after::text || '|' || operation_key FROM wallet_transactions WHERE operation_key='${operation_key}';" | tr -d '[:space:]')
test "$LEDGER_STATE" = "TOP_UP|12.34|12.34|${operation_key}"

BALANCE_AFTER_RETRY=$(docker compose exec -T db psql -U dofast -d dofast -tAc \
  "SELECT balance FROM wallets WHERE id=$WALLET_ID;" | tr -d '[:space:]')
test "$BALANCE_AFTER_RETRY" = '12.34'

FUNDING_STATE=$(docker compose exec -T db psql -U dofast -d dofast -tAc \
  "SELECT source_type || '|' || withdrawable || '|' || original_amount::text || '|' || remaining_amount::text FROM wallet_funding_lots WHERE wallet_id=$WALLET_ID;" | tr -d '[:space:]')
test "$FUNDING_STATE" = 'STRIPE_PAYMENT|false|12.34|12.34'

THIRD_STATUS=$(curl --silent --show-error \
  --output /tmp/webhook-crash-third.txt \
  --write-out '%{http_code}' \
  -H 'Content-Type: application/json' \
  -H "Stripe-Signature: ${SIGNATURE_HEADER}" \
  --data-binary "$PAYLOAD" \
  "$api/webhooks/stripe")
test "$THIRD_STATUS" = '200'
grep -q '^already processed$' /tmp/webhook-crash-third.txt

FINAL_COUNTS=$(docker compose exec -T db psql -U dofast -d dofast -tAc \
  "SELECT (SELECT count(*) FROM payment_transactions WHERE stripe_payment_intent_id='${payment_intent_id}') || '|' || (SELECT count(*) FROM wallet_transactions WHERE operation_key='${operation_key}') || '|' || (SELECT count(*) FROM wallet_funding_lots WHERE wallet_id=$WALLET_ID);" | tr -d '[:space:]')
test "$FINAL_COUNTS" = '1|1|1'

FINAL_BALANCE=$(docker compose exec -T db psql -U dofast -d dofast -tAc \
  "SELECT balance FROM wallets WHERE id=$WALLET_ID;" | tr -d '[:space:]')
test "$FINAL_BALANCE" = '12.34'

echo 'Stripe webhook claim/ledger transaction rolls back atomically and retries exactly once: OK'
