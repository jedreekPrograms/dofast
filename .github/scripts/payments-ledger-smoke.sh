#!/usr/bin/env bash
set -euo pipefail
trap 'echo "Payments smoke failed at line $LINENO"' ERR

api='http://localhost:8080'

register_and_login() {
  local email="$1"
  local nickname="$2"
  local prefix="$3"
  local registered
  local login

  registered=$(curl --fail --silent --show-error \
    -H 'Content-Type: application/json' \
    -d "{\"email\":\"$email\",\"nickname\":\"$nickname\",\"password\":\"LedgerPass123!\"}" \
    "$api/users")
  printf '%s' "$registered" > "/tmp/${prefix}-register.json"

  login=$(curl --fail --silent --show-error \
    -H 'Content-Type: application/json' \
    -d "{\"email\":\"$email\",\"password\":\"LedgerPass123!\"}" \
    "$api/users/login")
  printf '%s' "$login" > "/tmp/${prefix}-login.json"
}

create_route_quote() {
  local token="$1"
  local origin_private="$2"
  local destination_private="$3"
  local quote

  quote=$(curl --fail --silent --show-error \
    -H "Authorization: Bearer $token" \
    -H 'Content-Type: application/json' \
    -d "{\"origin\":{\"latitude\":51.1128,\"longitude\":17.0601,\"publicLabel\":\"Wrocław\",\"privateLabel\":\"$origin_private\"},\"destination\":{\"latitude\":51.1099,\"longitude\":17.0325,\"publicLabel\":\"Wrocław\",\"privateLabel\":\"$destination_private\"}}" \
    "$api/routing/quotes")
  python3 -c 'import json,sys; print(json.load(sys.stdin)["id"])' <<< "$quote"
}

expect_post_ok() {
  local label="$1"
  local token="$2"
  local url="$3"
  local output="$4"
  local status

  status=$(curl --silent --show-error --output "$output" --write-out '%{http_code}' \
    -X POST -H "Authorization: Bearer $token" "$url")
  if [[ ! "$status" =~ ^2[0-9][0-9]$ ]]; then
    echo "$label failed with HTTP $status"
    cat "$output" || true
    echo
    return 1
  fi
}

register_and_login 'ledger-requester@example.com' 'ledgerRequester' requester
register_and_login 'ledger-worker@example.com' 'ledgerWorker' worker
register_and_login 'ledger-refund@example.com' 'ledgerRefund' refund

REQUESTER_ID=$(python3 -c 'import json; print(json.load(open("/tmp/requester-register.json"))["id"])')
REQUESTER_TOKEN=$(python3 -c 'import json; print(json.load(open("/tmp/requester-login.json"))["accessToken"])')
WORKER_ID=$(python3 -c 'import json; print(json.load(open("/tmp/worker-register.json"))["id"])')
WORKER_TOKEN=$(python3 -c 'import json; print(json.load(open("/tmp/worker-login.json"))["accessToken"])')
REFUND_ID=$(python3 -c 'import json; print(json.load(open("/tmp/refund-register.json"))["id"])')
REFUND_TOKEN=$(python3 -c 'import json; print(json.load(open("/tmp/refund-login.json"))["accessToken"])')
CATEGORY_ID=$(docker compose exec -T db psql -U dofast -d dofast -tAc "SELECT id FROM job_categories WHERE slug='mala-paczka' AND active=TRUE;")
CATEGORY_ID="${CATEGORY_ID//[[:space:]]/}"
test -n "$CATEGORY_ID"

REQUESTER_WALLET_ID=$(docker compose exec -T db psql -U dofast -d dofast -tAc \
  "SELECT id FROM wallets WHERE user_id=$REQUESTER_ID;" | tr -d '[:space:]')
REFUND_WALLET_ID=$(docker compose exec -T db psql -U dofast -d dofast -tAc \
  "SELECT id FROM wallets WHERE user_id=$REFUND_ID;" | tr -d '[:space:]')
test -n "$REQUESTER_WALLET_ID"
test -n "$REFUND_WALLET_ID"

bash .github/scripts/seed-wallet-funding.sh \
  "$REQUESTER_ID" 50.00 PLATFORM_ADJUSTMENT \
  "smoke:ledger:requester:$REQUESTER_WALLET_ID" \
  "smoke:seed:requester:$REQUESTER_WALLET_ID"
bash .github/scripts/seed-wallet-funding.sh \
  "$REFUND_ID" 30.00 PLATFORM_ADJUSTMENT \
  "smoke:ledger:refund:$REFUND_WALLET_ID" \
  "smoke:seed:refund:$REFUND_WALLET_ID"

QUOTE_A=$(create_route_quote "$REQUESTER_TOKEN" 'Ledger A origin' 'Ledger A destination')
QUOTE_B=$(create_route_quote "$REQUESTER_TOKEN" 'Ledger B origin' 'Ledger B destination')

create_job() {
  local title="$1"
  local quote_id="$2"
  local output="$3"
  local status_file="$4"
  curl --silent --show-error \
    --output "$output" \
    --write-out '%{http_code}' \
    -H "Authorization: Bearer $REQUESTER_TOKEN" \
    -H 'Content-Type: application/json' \
    -d "{\"title\":\"$title\",\"description\":\"Concurrent wallet debit smoke test.\",\"price\":40.00,\"categoryId\":$CATEGORY_ID,\"routeQuoteId\":\"$quote_id\"}" \
    "$api/jobs" > "$status_file"
}

create_job 'Ledger concurrent A' "$QUOTE_A" /tmp/job-a.json /tmp/job-a.status &
PID_A=$!
create_job 'Ledger concurrent B' "$QUOTE_B" /tmp/job-b.json /tmp/job-b.status &
PID_B=$!
wait "$PID_A"
wait "$PID_B"

STATUS_A=$(cat /tmp/job-a.status)
STATUS_B=$(cat /tmp/job-b.status)
SORTED_STATUSES=$(printf '%s\n%s\n' "$STATUS_A" "$STATUS_B" | sort | tr '\n' ' ')
if test "$SORTED_STATUSES" != "201 400 "; then
  echo "Unexpected concurrent create statuses: A=$STATUS_A B=$STATUS_B"
  echo 'Job A response:'; cat /tmp/job-a.json; echo
  echo 'Job B response:'; cat /tmp/job-b.json; echo
  exit 1
fi

if test "$STATUS_A" = "201"; then
  SUCCESS_BODY=/tmp/job-a.json
else
  SUCCESS_BODY=/tmp/job-b.json
fi
JOB_ID=$(python3 -c 'import json,sys; print(json.load(open(sys.argv[1]))["id"])' "$SUCCESS_BODY")

REQUESTER_BALANCE=$(docker compose exec -T db psql -U dofast -d dofast -tAc \
  "SELECT balance FROM wallets WHERE user_id = $REQUESTER_ID;")
test "${REQUESTER_BALANCE//[[:space:]]/}" = "10.00"

REQUESTER_JOB_COUNT=$(docker compose exec -T db psql -U dofast -d dofast -tAc \
  "SELECT COUNT(*) FROM jobs WHERE created_by_id = $REQUESTER_ID AND title LIKE 'Ledger concurrent %';")
test "${REQUESTER_JOB_COUNT//[[:space:]]/}" = "1"

HELD_COUNT=$(docker compose exec -T db psql -U dofast -d dofast -tAc \
  "SELECT COUNT(*) FROM escrow_transactions WHERE payer_id = $REQUESTER_ID AND status = 'HELD';")
test "${HELD_COUNT//[[:space:]]/}" = "1"

LOCK_LEDGER=$(docker compose exec -T db psql -U dofast -d dofast -tAc \
  "SELECT amount || '|' || balance_after || '|' || operation_key FROM wallet_transactions WHERE wallet_id = (SELECT id FROM wallets WHERE user_id = $REQUESTER_ID) AND type = 'ESCROW_LOCK';")
test "${LOCK_LEDGER//[[:space:]]/}" = "-40.00|10.00|escrow:${JOB_ID}:lock"

expect_post_ok 'Accept job' "$WORKER_TOKEN" "$api/jobs/$JOB_ID/accept" /tmp/job-accept.json
python3 -c 'import json; assert json.load(open("/tmp/job-accept.json"))["status"] == "IN_PROGRESS"'
expect_post_ok 'Request completion' "$WORKER_TOKEN" "$api/jobs/$JOB_ID/completion" /tmp/job-completion.json
python3 -c 'import json; assert json.load(open("/tmp/job-completion.json"))["status"] == "COMPLETION_REQUESTED"'
expect_post_ok 'Confirm completion' "$REQUESTER_TOKEN" "$api/jobs/$JOB_ID/confirm" /tmp/job-confirm.json
python3 -c 'import json; assert json.load(open("/tmp/job-confirm.json"))["status"] == "DONE"'

WORKER_BALANCE=$(docker compose exec -T db psql -U dofast -d dofast -tAc \
  "SELECT balance FROM wallets WHERE user_id = $WORKER_ID;")
test "${WORKER_BALANCE//[[:space:]]/}" = "40.00"

RELEASE_LEDGER=$(docker compose exec -T db psql -U dofast -d dofast -tAc \
  "SELECT amount || '|' || balance_after || '|' || operation_key FROM wallet_transactions WHERE wallet_id = (SELECT id FROM wallets WHERE user_id = $WORKER_ID) AND type = 'ESCROW_RELEASE';")
test "${RELEASE_LEDGER//[[:space:]]/}" = "40.00|40.00|escrow:${JOB_ID}:release"

WORKER_FUNDING=$(docker compose exec -T db psql -U dofast -d dofast -tAc \
  "SELECT source_type || '|' || withdrawable || '|' || remaining_amount::text FROM wallet_funding_lots WHERE wallet_id=(SELECT id FROM wallets WHERE user_id=$WORKER_ID);" | tr -d '[:space:]')
test "$WORKER_FUNDING" = "EARNED_JOB|true|40.00"

ESCROW_RELEASED=$(docker compose exec -T db psql -U dofast -d dofast -tAc \
  "SELECT status || '|' || payee_id || '|' || (resolved_at IS NOT NULL) FROM escrow_transactions WHERE job_id = $JOB_ID;")
test "${ESCROW_RELEASED//[[:space:]]/}" = "RELEASED|${WORKER_ID}|true"

REFUND_QUOTE=$(create_route_quote "$REFUND_TOKEN" 'Refund origin' 'Refund destination')
REFUND_JOB=$(curl --fail --silent --show-error \
  -H "Authorization: Bearer $REFUND_TOKEN" \
  -H 'Content-Type: application/json' \
  -d "{\"title\":\"Ledger refund\",\"description\":\"Refund ledger smoke test.\",\"price\":15.00,\"categoryId\":$CATEGORY_ID,\"routeQuoteId\":\"$REFUND_QUOTE\"}" \
  "$api/jobs")
REFUND_JOB_ID=$(python3 -c 'import json,sys; print(json.load(sys.stdin)["id"])' <<< "$REFUND_JOB")

CANCELLED=$(curl --fail --silent --show-error -X POST \
  -H "Authorization: Bearer $REFUND_TOKEN" \
  "$api/jobs/$REFUND_JOB_ID/cancel")
echo "$CANCELLED" | grep -q '"status":"CANCELLED"'

REFUND_BALANCE=$(docker compose exec -T db psql -U dofast -d dofast -tAc \
  "SELECT balance FROM wallets WHERE user_id = $REFUND_ID;")
test "${REFUND_BALANCE//[[:space:]]/}" = "30.00"

REFUND_LEDGER=$(docker compose exec -T db psql -U dofast -d dofast -tAc \
  "SELECT string_agg(type || ':' || amount || ':' || balance_after || ':' || operation_key, ',' ORDER BY created_at, id) FROM wallet_transactions WHERE wallet_id = $REFUND_WALLET_ID;")
test "${REFUND_LEDGER//[[:space:]]/}" = "TOP_UP:30.00:30.00:smoke:seed:refund:${REFUND_WALLET_ID},ESCROW_LOCK:-15.00:15.00:escrow:${REFUND_JOB_ID}:lock,REFUND:15.00:30.00:escrow:${REFUND_JOB_ID}:refund"

REFUND_SOURCE=$(docker compose exec -T db psql -U dofast -d dofast -tAc \
  "SELECT source_type || '|' || withdrawable || '|' || remaining_amount::text FROM wallet_funding_lots WHERE wallet_id=$REFUND_WALLET_ID;" | tr -d '[:space:]')
test "$REFUND_SOURCE" = "PLATFORM_ADJUSTMENT|false|30.00"

REFUNDED_ESCROW=$(docker compose exec -T db psql -U dofast -d dofast -tAc \
  "SELECT status || '|' || (payee_id IS NULL) || '|' || (resolved_at IS NOT NULL) FROM escrow_transactions WHERE job_id = $REFUND_JOB_ID;")
test "${REFUNDED_ESCROW//[[:space:]]/}" = "REFUNDED|true|true"

OPERATION_UNIQUE=$(docker compose exec -T db psql -U dofast -d dofast -tAc \
  "SELECT COUNT(*) FROM pg_constraint WHERE conname = 'uk_wallet_transactions_operation';")
test "${OPERATION_UNIQUE//[[:space:]]/}" = "1"

EVENT_UNIQUE=$(docker compose exec -T db psql -U dofast -d dofast -tAc \
  "SELECT COUNT(*) FROM pg_constraint WHERE conname = 'uk_payment_transactions_stripe_event';")
test "${EVENT_UNIQUE//[[:space:]]/}" = "1"

BAD_INTENT_STATUS=$(curl --silent --show-error --output /tmp/bad-intent.json --write-out '%{http_code}' \
  -H "Authorization: Bearer $REQUESTER_TOKEN" \
  -H 'Content-Type: application/json' \
  -d '{"amount":10.00}' \
  "$api/payments/create-intent")
test "$BAD_INTENT_STATUS" = "400"

INVALID_SIGNATURE_STATUS=$(curl --silent --show-error --output /tmp/invalid-webhook.txt --write-out '%{http_code}' \
  -H 'Content-Type: application/json' \
  -H 'Stripe-Signature: t=1,v1=invalid' \
  -d '{}' \
  "$api/webhooks/stripe")
test "$INVALID_SIGNATURE_STATUS" = "400"

ADMIN_LOGIN=$(curl --fail --silent --show-error \
  -H 'Content-Type: application/json' \
  -d '{"email":"admin-ledger@example.com","password":"AdminLedgerPass123!"}' \
  "$api/users/login")
ADMIN_TOKEN=$(python3 -c 'import json,sys; print(json.load(sys.stdin)["accessToken"])' <<< "$ADMIN_LOGIN")

RECONCILIATION=$(curl --fail --silent --show-error \
  -H "Authorization: Bearer $ADMIN_TOKEN" \
  "$api/admin/finance/reconciliation")
python3 -c 'import json,sys; d=json.load(sys.stdin); assert d["healthy"] is True; assert d["walletBalanceMismatches"] == 0; assert d["ledgerSequenceMismatches"] == 0; assert d["stripeLedgerMismatches"] == 0; assert d["heldEscrowCount"] == 0; assert float(d["heldEscrowAmount"]) == 0.0; assert d["processedStripePayments"] == 0' <<< "$RECONCILIATION"

# Intentional corruption: reconciliation must detect a wallet balance that bypasses the ledger.
docker compose exec -T db psql -U dofast -d dofast -v ON_ERROR_STOP=1 -c \
  "UPDATE wallets w SET balance = 1.00 FROM users u WHERE w.user_id = u.id AND u.email = 'admin-ledger@example.com';"
BROKEN_BALANCE=$(curl --fail --silent --show-error \
  -H "Authorization: Bearer $ADMIN_TOKEN" \
  "$api/admin/finance/reconciliation")
python3 -c 'import json,sys; d=json.load(sys.stdin); assert d["healthy"] is False; assert d["walletBalanceMismatches"] == 1; assert d["ledgerSequenceMismatches"] == 0; assert d["stripeLedgerMismatches"] == 0' <<< "$BROKEN_BALANCE"

# Intentional orphan Stripe ledger fixture: keep these raw writes so reconciliation observes the mismatch.
docker compose exec -T db psql -U dofast -d dofast -v ON_ERROR_STOP=1 <<'SQL'
UPDATE wallets w
SET balance = 0.00
FROM users u
WHERE w.user_id = u.id
  AND u.email = 'admin-ledger@example.com';

UPDATE wallets w
SET balance = 1.00
FROM users u
WHERE w.user_id = u.id
  AND u.email = 'admin-ledger@example.com';

INSERT INTO wallet_transactions (
    wallet_id, type, amount, job_id, created_at, operation_key, balance_after
)
SELECT w.id, 'TOP_UP', 1.00, NULL, CURRENT_TIMESTAMP, 'stripe:intent:pi_smoke_orphan', 1.00
FROM wallets w
JOIN users u ON u.id = w.user_id
WHERE u.email = 'admin-ledger@example.com';
SQL

ORPHAN_TOPUP=$(curl --fail --silent --show-error \
  -H "Authorization: Bearer $ADMIN_TOKEN" \
  "$api/admin/finance/reconciliation")
python3 -c 'import json,sys; d=json.load(sys.stdin); assert d["healthy"] is False; assert d["walletBalanceMismatches"] == 0; assert d["ledgerSequenceMismatches"] == 0; assert d["stripeLedgerMismatches"] == 1; assert d["processedStripePayments"] == 0' <<< "$ORPHAN_TOPUP"

docker compose exec -T db psql -U dofast -d dofast -v ON_ERROR_STOP=1 <<'SQL'
INSERT INTO payment_transactions (
    stripe_payment_intent_id,
    stripe_event_id,
    user_id,
    amount,
    currency,
    settlement_purpose,
    business_reference,
    processed_at
)
SELECT 'pi_smoke_orphan', 'evt_smoke_orphan', u.id, 1.00, 'PLN', 'TOP_UP', 'smoke-orphan', CURRENT_TIMESTAMP
FROM users u
WHERE u.email = 'admin-ledger@example.com';
SQL

HEALED_RECONCILIATION=$(curl --fail --silent --show-error \
  -H "Authorization: Bearer $ADMIN_TOKEN" \
  "$api/admin/finance/reconciliation")
python3 -c 'import json,sys; d=json.load(sys.stdin); assert d["healthy"] is True; assert d["walletBalanceMismatches"] == 0; assert d["ledgerSequenceMismatches"] == 0; assert d["stripeLedgerMismatches"] == 0; assert d["processedStripePayments"] == 1' <<< "$HEALED_RECONCILIATION"

docker compose exec -T db psql -U dofast -d dofast -v ON_ERROR_STOP=1 -c \
  "UPDATE payment_transactions SET settlement_purpose = 'JOB_PUBLICATION', business_reference = '999999999' WHERE stripe_payment_intent_id = 'pi_smoke_orphan';"
BROKEN_SETTLEMENT_IDENTITY=$(curl --fail --silent --show-error \
  -H "Authorization: Bearer $ADMIN_TOKEN" \
  "$api/admin/finance/reconciliation")
python3 -c 'import json,sys; d=json.load(sys.stdin); assert d["healthy"] is False; assert d["stripeLedgerMismatches"] == 1; assert d["processedStripePayments"] == 1' <<< "$BROKEN_SETTLEMENT_IDENTITY"

docker compose exec -T db psql -U dofast -d dofast -v ON_ERROR_STOP=1 -c \
  "UPDATE payment_transactions SET settlement_purpose = 'TOP_UP', business_reference = 'smoke-orphan' WHERE stripe_payment_intent_id = 'pi_smoke_orphan';"
FINAL_RECONCILIATION=$(curl --fail --silent --show-error \
  -H "Authorization: Bearer $ADMIN_TOKEN" \
  "$api/admin/finance/reconciliation")
python3 -c 'import json,sys; d=json.load(sys.stdin); assert d["healthy"] is True; assert d["stripeLedgerMismatches"] == 0; assert d["processedStripePayments"] == 1' <<< "$FINAL_RECONCILIATION"
