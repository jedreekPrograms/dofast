#!/usr/bin/env bash
set -euo pipefail
trap 'echo "Job publication settlement crash smoke failed at line $LINENO"' ERR

api='http://localhost:8080'
webhook_secret=$(sed -n 's/^STRIPE_WEBHOOK_SECRET=//p' .env | tail -n 1)
test -n "$webhook_secret"

request_id='settlement-crash-001'
payment_intent_id='pi_job_publication_settlement_crash'
event_id='evt_job_publication_settlement_crash'
stripe_operation_key="stripe:intent:${payment_intent_id}"
trigger_name='smoke_fail_publication_escrow_insert'
function_name='smoke_fail_publication_escrow_insert_fn'

cleanup_failpoint() {
  docker compose exec -T db psql -U dofast -d dofast -v ON_ERROR_STOP=1 >/dev/null 2>&1 <<SQL || true
DROP TRIGGER IF EXISTS ${trigger_name} ON escrow_transactions;
DROP FUNCTION IF EXISTS ${function_name}();
SQL
}
trap 'cleanup_failpoint' EXIT

REGISTERED=$(curl --fail --silent --show-error \
  -H 'Content-Type: application/json' \
  -d '{"email":"publication-settlement-crash@example.com","nickname":"publicationSettlementCrash","password":"PublicationCrashPass123!"}' \
  "$api/users")
USER_ID=$(python3 -c 'import json,sys; print(json.load(sys.stdin)["id"])' <<< "$REGISTERED")
test -n "$USER_ID"

LOGIN=$(curl --fail --silent --show-error \
  -H 'Content-Type: application/json' \
  -d '{"email":"publication-settlement-crash@example.com","password":"PublicationCrashPass123!"}' \
  "$api/users/login")
TOKEN=$(python3 -c 'import json,sys; print(json.load(sys.stdin)["accessToken"])' <<< "$LOGIN")
test -n "$TOKEN"

CATEGORY_ID=$(docker compose exec -T db psql -U dofast -d dofast -tAc \
  "SELECT id FROM job_categories WHERE slug='montaz-mebli' AND active=TRUE;")
CATEGORY_ID="${CATEGORY_ID//[[:space:]]/}"
test -n "$CATEGORY_ID"

WALLET_ID=$(docker compose exec -T db psql -U dofast -d dofast -tAc \
  "SELECT id FROM wallets WHERE user_id=$USER_ID;" | tr -d '[:space:]')
test -n "$WALLET_ID"

bash .github/scripts/seed-wallet-funding.sh \
  "$USER_ID" 25.00 PLATFORM_ADJUSTMENT \
  'smoke:publication-settlement-crash:source' \
  'smoke:publication-settlement-crash:seed'

PUBLICATION_PAYLOAD=$(cat <<JSON
{"requestId":"${request_id}","job":{"title":"Publication settlement crash smoke","description":"A real PostgreSQL rollback must undo the complete publication settlement.","price":70.00,"categoryId":$CATEGORY_ID,"location":{"latitude":51.1100,"longitude":17.0300,"publicLabel":"Wrocław","privateLabel":"ul. Settlement Crash 1, Wrocław"}}}
JSON
)

PUBLICATION=$(curl --fail --silent --show-error \
  -H "Authorization: Bearer $TOKEN" \
  -H 'Content-Type: application/json' \
  -d "$PUBLICATION_PAYLOAD" \
  "$api/jobs/publications")
printf '%s' "$PUBLICATION" > /tmp/publication-settlement-crash.json
PUBLICATION_ID=$(python3 -c 'import json; print(json.load(open("/tmp/publication-settlement-crash.json"))["id"])')
test -n "$PUBLICATION_ID"

python3 - <<'PY'
import json
p=json.load(open('/tmp/publication-settlement-crash.json'))
assert p['status']=='PAYMENT_REQUIRED', p
assert float(p['totalAmount'])==70.0, p
assert float(p['walletReservedAmount'])==25.0, p
assert float(p['paymentAmount'])==45.0, p
assert p['jobId'] is None, p
assert p['paymentReceivedAt'] is None, p
PY

BALANCE_BEFORE=$(docker compose exec -T db psql -U dofast -d dofast -tAc \
  "SELECT balance FROM wallets WHERE id=$WALLET_ID;" | tr -d '[:space:]')
test "$BALANCE_BEFORE" = '0.00'

RESERVE_KEY="job-publication:${USER_ID}:${request_id}:reserve"
RELEASE_KEY="job-publication:${PUBLICATION_ID}:release"
RESERVE_COUNT=$(docker compose exec -T db psql -U dofast -d dofast -tAc \
  "SELECT count(*) FROM wallet_transactions WHERE operation_key='${RESERVE_KEY}' AND type='JOB_PUBLICATION_RESERVE' AND amount=-25.00;" | tr -d '[:space:]')
test "$RESERVE_COUNT" = '1'

TIMESTAMP=$(date +%s)
PAYLOAD=$(python3 - "$USER_ID" "$PUBLICATION_ID" "$TIMESTAMP" <<'PY'
import json
import sys

user_id, publication_id, created = sys.argv[1], sys.argv[2], int(sys.argv[3])
event = {
    "id": "evt_job_publication_settlement_crash",
    "object": "event",
    "api_version": "2026-07-29.dahlia",
    "created": created,
    "data": {
        "object": {
            "id": "pi_job_publication_settlement_crash",
            "object": "payment_intent",
            "amount": 4500,
            "currency": "pln",
            "livemode": False,
            "metadata": {
                "userId": user_id,
                "purpose": "JOB_PUBLICATION",
                "jobPublicationId": publication_id
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

# Fail at the final escrow row creation for this requester/amount. By this point the
# publication settlement has already claimed the Stripe event, credited the 45 PLN,
# restored the 25 PLN publication reservation, inserted the Job and debited 70 PLN
# for escrow. The exception must roll the entire enclosing Spring transaction back.
docker compose exec -T db psql -U dofast -d dofast -v ON_ERROR_STOP=1 >/dev/null <<SQL
CREATE OR REPLACE FUNCTION ${function_name}()
RETURNS trigger
LANGUAGE plpgsql
AS \$\$
BEGIN
  IF NEW.payer_id = ${USER_ID} AND NEW.amount = 70.00 THEN
    RAISE EXCEPTION 'intentional job publication settlement crash smoke';
  END IF;
  RETURN NEW;
END;
\$\$;

CREATE TRIGGER ${trigger_name}
BEFORE INSERT ON escrow_transactions
FOR EACH ROW
EXECUTE FUNCTION ${function_name}();
SQL

FIRST_STATUS=$(curl --silent --show-error \
  --output /tmp/publication-settlement-crash-first.txt \
  --write-out '%{http_code}' \
  -H 'Content-Type: application/json' \
  -H "Stripe-Signature: ${SIGNATURE_HEADER}" \
  --data-binary "$PAYLOAD" \
  "$api/webhooks/stripe")
test "$FIRST_STATUS" = '500'
grep -q 'processing failed' /tmp/publication-settlement-crash-first.txt

CLAIM_AFTER_FAILURE=$(docker compose exec -T db psql -U dofast -d dofast -tAc \
  "SELECT count(*) FROM payment_transactions WHERE stripe_payment_intent_id='${payment_intent_id}' OR stripe_event_id='${event_id}';" | tr -d '[:space:]')
STRIPE_LEDGER_AFTER_FAILURE=$(docker compose exec -T db psql -U dofast -d dofast -tAc \
  "SELECT count(*) FROM wallet_transactions WHERE operation_key='${stripe_operation_key}';" | tr -d '[:space:]')
RELEASE_AFTER_FAILURE=$(docker compose exec -T db psql -U dofast -d dofast -tAc \
  "SELECT count(*) FROM wallet_transactions WHERE operation_key='${RELEASE_KEY}';" | tr -d '[:space:]')
JOB_AFTER_FAILURE=$(docker compose exec -T db psql -U dofast -d dofast -tAc \
  "SELECT count(*) FROM jobs WHERE created_by_id=$USER_ID AND title='Publication settlement crash smoke';" | tr -d '[:space:]')
ESCROW_AFTER_FAILURE=$(docker compose exec -T db psql -U dofast -d dofast -tAc \
  "SELECT count(*) FROM escrow_transactions WHERE payer_id=$USER_ID AND amount=70.00;" | tr -d '[:space:]')
STRIPE_LOTS_AFTER_FAILURE=$(docker compose exec -T db psql -U dofast -d dofast -tAc \
  "SELECT count(*) FROM wallet_funding_lots WHERE wallet_id=$WALLET_ID AND source_type='STRIPE_PAYMENT';" | tr -d '[:space:]')
BALANCE_AFTER_FAILURE=$(docker compose exec -T db psql -U dofast -d dofast -tAc \
  "SELECT balance FROM wallets WHERE id=$WALLET_ID;" | tr -d '[:space:]')
PUBLICATION_AFTER_FAILURE=$(docker compose exec -T db psql -U dofast -d dofast -tAc \
  "SELECT status || '|' || CASE WHEN payment_received_at IS NULL THEN '1' ELSE '0' END || '|' || CASE WHEN published_job_id IS NULL THEN '1' ELSE '0' END || '|' || CASE WHEN stripe_payment_intent_id IS NULL THEN '1' ELSE '0' END || '|' || CASE WHEN request_payload IS NOT NULL THEN '1' ELSE '0' END FROM job_publications WHERE id=$PUBLICATION_ID;" | tr -d '[:space:]')
RESERVE_AFTER_FAILURE=$(docker compose exec -T db psql -U dofast -d dofast -tAc \
  "SELECT count(*) FROM wallet_transactions WHERE operation_key='${RESERVE_KEY}';" | tr -d '[:space:]')

# Everything created by the failed webhook must disappear. The pre-existing publication
# reservation remains exactly as it was before webhook delivery.
test "$CLAIM_AFTER_FAILURE" = '0'
test "$STRIPE_LEDGER_AFTER_FAILURE" = '0'
test "$RELEASE_AFTER_FAILURE" = '0'
test "$JOB_AFTER_FAILURE" = '0'
test "$ESCROW_AFTER_FAILURE" = '0'
test "$STRIPE_LOTS_AFTER_FAILURE" = '0'
test "$BALANCE_AFTER_FAILURE" = '0.00'
test "$PUBLICATION_AFTER_FAILURE" = 'PAYMENT_REQUIRED|1|1|1|1'
test "$RESERVE_AFTER_FAILURE" = '1'

cleanup_failpoint
trap - EXIT

SECOND_STATUS=$(curl --silent --show-error \
  --output /tmp/publication-settlement-crash-second.txt \
  --write-out '%{http_code}' \
  -H 'Content-Type: application/json' \
  -H "Stripe-Signature: ${SIGNATURE_HEADER}" \
  --data-binary "$PAYLOAD" \
  "$api/webhooks/stripe")
test "$SECOND_STATUS" = '200'
grep -q '^ok$' /tmp/publication-settlement-crash-second.txt

PAYMENT_STATE=$(docker compose exec -T db psql -U dofast -d dofast -tAc \
  "SELECT stripe_payment_intent_id || '|' || stripe_event_id || '|' || user_id || '|' || amount::text || '|' || currency || '|' || settlement_purpose || '|' || business_reference FROM payment_transactions WHERE stripe_payment_intent_id='${payment_intent_id}';" | tr -d '[:space:]')
test "$PAYMENT_STATE" = "${payment_intent_id}|${event_id}|${USER_ID}|45.00|PLN|JOB_PUBLICATION|${PUBLICATION_ID}"

PUBLICATION_STATE=$(docker compose exec -T db psql -U dofast -d dofast -tAc \
  "SELECT status || '|' || CASE WHEN payment_received_at IS NOT NULL THEN '1' ELSE '0' END || '|' || published_job_id || '|' || stripe_payment_intent_id || '|' || CASE WHEN request_payload IS NULL THEN '1' ELSE '0' END FROM job_publications WHERE id=$PUBLICATION_ID;" | tr -d '[:space:]')
IFS='|' read -r PUB_STATUS PUB_PAID JOB_ID PUB_PI PUB_PAYLOAD_CLEARED <<< "$PUBLICATION_STATE"
test "$PUB_STATUS" = 'PUBLISHED'
test "$PUB_PAID" = '1'
test -n "$JOB_ID"
test "$PUB_PI" = "$payment_intent_id"
test "$PUB_PAYLOAD_CLEARED" = '1'

JOB_STATE=$(docker compose exec -T db psql -U dofast -d dofast -tAc \
  "SELECT status || '|' || price::text || '|' || created_by_id FROM jobs WHERE id=$JOB_ID AND title='Publication settlement crash smoke';" | tr -d '[:space:]')
test "$JOB_STATE" = "OPEN|70.00|${USER_ID}"

ESCROW_STATE=$(docker compose exec -T db psql -U dofast -d dofast -tAc \
  "SELECT status || '|' || amount::text || '|' || payer_id FROM escrow_transactions WHERE job_id=$JOB_ID;" | tr -d '[:space:]')
test "$ESCROW_STATE" = "HELD|70.00|${USER_ID}"

LEDGER_STATE=$(docker compose exec -T db psql -U dofast -d dofast -tAc \
  "SELECT type || '|' || amount::text || '|' || operation_key FROM wallet_transactions WHERE operation_key IN ('${stripe_operation_key}','${RELEASE_KEY}','escrow:${JOB_ID}:lock') ORDER BY operation_key;" | tr -d '[:space:]')
[[ "$LEDGER_STATE" == *"JOB_PUBLICATION_FUNDING|45.00|${stripe_operation_key}"* ]]
[[ "$LEDGER_STATE" == *"JOB_PUBLICATION_RELEASE|25.00|${RELEASE_KEY}"* ]]
[[ "$LEDGER_STATE" == *"ESCROW_LOCK|-70.00|escrow:${JOB_ID}:lock"* ]]

FINAL_BALANCE=$(docker compose exec -T db psql -U dofast -d dofast -tAc \
  "SELECT balance FROM wallets WHERE id=$WALLET_ID;" | tr -d '[:space:]')
test "$FINAL_BALANCE" = '0.00'

FINAL_FUNDING_REMAINING=$(docker compose exec -T db psql -U dofast -d dofast -tAc \
  "SELECT COALESCE(sum(remaining_amount),0)::text FROM wallet_funding_lots WHERE wallet_id=$WALLET_ID;" | tr -d '[:space:]')
test "$FINAL_FUNDING_REMAINING" = '0.00'

THIRD_STATUS=$(curl --silent --show-error \
  --output /tmp/publication-settlement-crash-third.txt \
  --write-out '%{http_code}' \
  -H 'Content-Type: application/json' \
  -H "Stripe-Signature: ${SIGNATURE_HEADER}" \
  --data-binary "$PAYLOAD" \
  "$api/webhooks/stripe")
test "$THIRD_STATUS" = '200'
grep -q '^already processed$' /tmp/publication-settlement-crash-third.txt

FINAL_COUNTS=$(docker compose exec -T db psql -U dofast -d dofast -tAc \
  "SELECT (SELECT count(*) FROM payment_transactions WHERE stripe_payment_intent_id='${payment_intent_id}') || '|' || (SELECT count(*) FROM jobs WHERE id=$JOB_ID) || '|' || (SELECT count(*) FROM escrow_transactions WHERE job_id=$JOB_ID) || '|' || (SELECT count(*) FROM wallet_transactions WHERE operation_key='${stripe_operation_key}') || '|' || (SELECT count(*) FROM wallet_transactions WHERE operation_key='${RELEASE_KEY}') || '|' || (SELECT count(*) FROM wallet_transactions WHERE operation_key='escrow:${JOB_ID}:lock');" | tr -d '[:space:]')
test "$FINAL_COUNTS" = '1|1|1|1|1|1'

echo 'Job publication settlement rolls back atomically after a late escrow failure and the identical signed webhook retries exactly once: OK'
