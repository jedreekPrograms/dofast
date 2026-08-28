#!/usr/bin/env bash
set -euo pipefail
trap 'echo "Platform fee smoke failed at line $LINENO"' ERR

api='http://localhost:8080'

register_and_login() {
  local email="$1"
  local nickname="$2"
  local prefix="$3"

  curl --fail --silent --show-error \
    -H 'Content-Type: application/json' \
    -d "{\"email\":\"$email\",\"nickname\":\"$nickname\",\"password\":\"FeePass123!\"}" \
    "$api/users" > "/tmp/${prefix}-register.json"

  curl --fail --silent --show-error \
    -H 'Content-Type: application/json' \
    -d "{\"email\":\"$email\",\"password\":\"FeePass123!\"}" \
    "$api/users/login" > "/tmp/${prefix}-login.json"
}

json_value() {
  local file="$1"
  local key="$2"
  python3 -c 'import json,sys; print(json.load(open(sys.argv[1]))[sys.argv[2]])' "$file" "$key"
}

register_and_login 'fee-requester@example.com' 'feeRequester' requester
register_and_login 'fee-worker@example.com' 'feeWorker' worker

REQUESTER_ID=$(json_value /tmp/requester-register.json id)
REQUESTER_TOKEN=$(json_value /tmp/requester-login.json accessToken)
WORKER_ID=$(json_value /tmp/worker-register.json id)
WORKER_TOKEN=$(json_value /tmp/worker-login.json accessToken)
CATEGORY_ID=$(docker compose exec -T db psql -U dofast -d dofast -tAc \
  "SELECT id FROM job_categories WHERE slug='montaz-mebli' AND fulfillment_mode='ON_SITE' AND active=TRUE;" | tr -d '[:space:]')
test -n "$CATEGORY_ID"

docker compose exec -T db psql -U dofast -d dofast -v ON_ERROR_STOP=1 <<SQL
BEGIN;
UPDATE wallets SET balance = 100.00 WHERE user_id = $REQUESTER_ID;
INSERT INTO wallet_transactions (
    wallet_id, type, amount, job_id, created_at, operation_key, balance_after
)
SELECT id, 'TOP_UP', 100.00, NULL, CURRENT_TIMESTAMP, 'smoke:fee:seed:' || id, 100.00
FROM wallets WHERE user_id = $REQUESTER_ID;
COMMIT;
SQL

POLICY=$(curl --fail --silent --show-error \
  -H "Authorization: Bearer $REQUESTER_TOKEN" \
  "$api/payments/platform-fee-policy")
python3 -c 'import json,sys; d=json.load(sys.stdin); assert d["basisPoints"] == 100; assert float(d["percent"]) == 1.0' <<< "$POLICY"

QUOTE=$(curl --fail --silent --show-error \
  -H "Authorization: Bearer $REQUESTER_TOKEN" \
  "$api/payments/platform-fee-quote?amount=33.50")
python3 -c 'import json,sys; d=json.load(sys.stdin); assert float(d["grossAmount"]) == 33.50; assert float(d["platformFeeAmount"]) == 0.34; assert float(d["workerPayoutAmount"]) == 33.16; assert d["basisPoints"] == 100' <<< "$QUOTE"

echo 'Platform fee policy/quote: OK'

JOB=$(curl --fail --silent --show-error \
  -H "Authorization: Bearer $REQUESTER_TOKEN" \
  -H 'Content-Type: application/json' \
  -d "{\"title\":\"Platform fee settlement smoke\",\"description\":\"Verify gross, fee and net settlement accounting.\",\"price\":40.00,\"categoryId\":$CATEGORY_ID,\"location\":{\"latitude\":51.1000,\"longitude\":17.0300,\"publicLabel\":\"Wrocław\",\"privateLabel\":\"Fee smoke address\"}}" \
  "$api/jobs")
printf '%s' "$JOB" > /tmp/fee-job.json
JOB_ID=$(json_value /tmp/fee-job.json id)

HELD=$(docker compose exec -T db psql -U dofast -d dofast -tAc \
  "SELECT amount || '|' || platform_fee_basis_points || '|' || (platform_fee_amount IS NULL) || '|' || (payee_amount IS NULL) FROM escrow_transactions WHERE job_id=$JOB_ID;" | tr -d '[:space:]')
test "$HELD" = "40.00|100|true|true"

REQUESTER_AFTER_HOLD=$(docker compose exec -T db psql -U dofast -d dofast -tAc \
  "SELECT balance FROM wallets WHERE user_id=$REQUESTER_ID;" | tr -d '[:space:]')
test "$REQUESTER_AFTER_HOLD" = "60.00"

curl --fail --silent --show-error --output /tmp/fee-accept.json -X POST \
  -H "Authorization: Bearer $WORKER_TOKEN" "$api/jobs/$JOB_ID/accept"
curl --fail --silent --show-error --output /tmp/fee-completion.json -X POST \
  -H "Authorization: Bearer $WORKER_TOKEN" "$api/jobs/$JOB_ID/completion"
curl --fail --silent --show-error --output /tmp/fee-confirm.json -X POST \
  -H "Authorization: Bearer $REQUESTER_TOKEN" "$api/jobs/$JOB_ID/confirm"
python3 -c 'import json; assert json.load(open("/tmp/fee-confirm.json"))["status"] == "DONE"'

WORKER_BALANCE=$(docker compose exec -T db psql -U dofast -d dofast -tAc \
  "SELECT balance FROM wallets WHERE user_id=$WORKER_ID;" | tr -d '[:space:]')
test "$WORKER_BALANCE" = "39.60"

RELEASE_LEDGER=$(docker compose exec -T db psql -U dofast -d dofast -tAc \
  "SELECT amount || '|' || balance_after || '|' || operation_key FROM wallet_transactions WHERE wallet_id=(SELECT id FROM wallets WHERE user_id=$WORKER_ID) AND type='ESCROW_RELEASE';" | tr -d '[:space:]')
test "$RELEASE_LEDGER" = "39.60|39.60|escrow:${JOB_ID}:release"

ESCROW_RELEASE=$(docker compose exec -T db psql -U dofast -d dofast -tAc \
  "SELECT amount || '|' || platform_fee_basis_points || '|' || platform_fee_amount || '|' || payee_amount || '|' || status || '|' || payee_id FROM escrow_transactions WHERE job_id=$JOB_ID;" | tr -d '[:space:]')
test "$ESCROW_RELEASE" = "40.00|100|0.40|39.60|RELEASED|${WORKER_ID}"

REVENUE=$(docker compose exec -T db psql -U dofast -d dofast -tAc \
  "SELECT type || '|' || amount || '|' || operation_key FROM platform_revenue_entries WHERE job_id=$JOB_ID;" | tr -d '[:space:]')
test "$REVENUE" = "PLATFORM_FEE|0.40|platform-fee:job:${JOB_ID}:release"

echo 'Gross -> platform fee + worker payout settlement: OK'

REFUND_JOB=$(curl --fail --silent --show-error \
  -H "Authorization: Bearer $REQUESTER_TOKEN" \
  -H 'Content-Type: application/json' \
  -d "{\"title\":\"Platform fee refund smoke\",\"description\":\"A refund must never create platform fee revenue.\",\"price\":15.00,\"categoryId\":$CATEGORY_ID,\"location\":{\"latitude\":51.1010,\"longitude\":17.0310,\"publicLabel\":\"Wrocław\",\"privateLabel\":\"Refund fee smoke address\"}}" \
  "$api/jobs")
printf '%s' "$REFUND_JOB" > /tmp/fee-refund-job.json
REFUND_JOB_ID=$(json_value /tmp/fee-refund-job.json id)

curl --fail --silent --show-error --output /tmp/fee-refund-cancel.json -X POST \
  -H "Authorization: Bearer $REQUESTER_TOKEN" "$api/jobs/$REFUND_JOB_ID/cancel"

REFUND_ESCROW=$(docker compose exec -T db psql -U dofast -d dofast -tAc \
  "SELECT status || '|' || (platform_fee_amount IS NULL) || '|' || (payee_amount IS NULL) FROM escrow_transactions WHERE job_id=$REFUND_JOB_ID;" | tr -d '[:space:]')
test "$REFUND_ESCROW" = "REFUNDED|true|true"

REFUND_REVENUE_COUNT=$(docker compose exec -T db psql -U dofast -d dofast -tAc \
  "SELECT count(*) FROM platform_revenue_entries WHERE job_id=$REFUND_JOB_ID;" | tr -d '[:space:]')
test "$REFUND_REVENUE_COUNT" = "0"

REQUESTER_AFTER_REFUND=$(docker compose exec -T db psql -U dofast -d dofast -tAc \
  "SELECT balance FROM wallets WHERE user_id=$REQUESTER_ID;" | tr -d '[:space:]')
test "$REQUESTER_AFTER_REFUND" = "60.00"

echo 'Refund does not charge platform fee: OK'

ADMIN_LOGIN=$(curl --fail --silent --show-error \
  -H 'Content-Type: application/json' \
  -d '{"email":"admin-fee@example.com","password":"AdminFeePass123!"}' \
  "$api/users/login")
ADMIN_TOKEN=$(python3 -c 'import json,sys; print(json.load(sys.stdin)["accessToken"])' <<< "$ADMIN_LOGIN")

RECONCILIATION=$(curl --fail --silent --show-error \
  -H "Authorization: Bearer $ADMIN_TOKEN" \
  "$api/admin/finance/reconciliation")
python3 -c 'import json,sys; d=json.load(sys.stdin); assert d["healthy"] is True; assert d["platformRevenueMismatches"] == 0; assert float(d["platformFeeRevenueAmount"]) == 0.40' <<< "$RECONCILIATION"

echo 'Platform revenue reconciliation: OK'
