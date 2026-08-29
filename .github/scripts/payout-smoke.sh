#!/usr/bin/env bash
set -euo pipefail
trap 'echo "Payout smoke failed at line $LINENO"' ERR

api='http://localhost:8080'

json_value() {
  local file="$1"
  local key="$2"
  python3 -c 'import json,sys; print(json.load(open(sys.argv[1]))[sys.argv[2]])' "$file" "$key"
}

register_and_login() {
  local email="$1"
  local nickname="$2"
  local prefix="$3"

  curl --fail --silent --show-error \
    -H 'Content-Type: application/json' \
    -d "{\"email\":\"$email\",\"nickname\":\"$nickname\",\"password\":\"PayoutPass123!\"}" \
    "$api/users" > "/tmp/${prefix}-register.json"

  curl --fail --silent --show-error \
    -H 'Content-Type: application/json' \
    -d "{\"email\":\"$email\",\"password\":\"PayoutPass123!\"}" \
    "$api/users/login" > "/tmp/${prefix}-login.json"
}

register_and_login 'payout-worker@example.com' 'payoutWorker' worker
USER_ID=$(json_value /tmp/worker-register.json id)
USER_TOKEN=$(json_value /tmp/worker-login.json accessToken)

ADMIN_LOGIN=$(curl --fail --silent --show-error \
  -H 'Content-Type: application/json' \
  -d '{"email":"admin-payout@example.com","password":"AdminPayoutPass123!"}' \
  "$api/users/login")
ADMIN_TOKEN=$(python3 -c 'import json,sys; print(json.load(sys.stdin)["accessToken"])' <<< "$ADMIN_LOGIN")

RECIPIENT_TABLE=$(docker compose exec -T db psql -U dofast -d dofast -tAc \
  "SELECT count(*) FROM information_schema.tables WHERE table_schema='public' AND table_name='payout_recipient_accounts';" | tr -d '[:space:]')
test "$RECIPIENT_TABLE" = "1"
ASYNC_COLUMNS=$(docker compose exec -T db psql -U dofast -d dofast -tAc \
  "SELECT count(*) FROM information_schema.columns WHERE table_name='payout_requests' AND column_name='provider_submitted_at';" | tr -d '[:space:]')
test "$ASYNC_COLUMNS" = "1"
TRANSFER_REFERENCE_COLUMN=$(docker compose exec -T db psql -U dofast -d dofast -tAc \
  "SELECT count(*) FROM information_schema.columns WHERE table_name='payout_requests' AND column_name='provider_transfer_reference';" | tr -d '[:space:]')
test "$TRANSFER_REFERENCE_COLUMN" = "1"
PROVIDER_EVENTS_TABLE=$(docker compose exec -T db psql -U dofast -d dofast -tAc \
  "SELECT count(*) FROM information_schema.tables WHERE table_schema='public' AND table_name='payout_provider_events';" | tr -d '[:space:]')
test "$PROVIDER_EVENTS_TABLE" = "1"

# Payout eligibility must be backed by money actually earned in doFast, never by card funding.
bash .github/scripts/seed-wallet-funding.sh \
  "$USER_ID" 50.00 EARNED_JOB \
  "smoke:payout:earned:$USER_ID" \
  "smoke:payout:seed:$USER_ID" \
  ESCROW_RELEASE

ELIGIBILITY=$(curl --fail --silent --show-error \
  -H "Authorization: Bearer $USER_TOKEN" "$api/wallet/payouts/eligibility")
python3 -c 'import json,sys; d=json.load(sys.stdin); assert d["identityVerified"] is False; assert d["providerAvailable"] is True; assert d["providerMode"] == "SANDBOX"; assert d["recipientReady"] is False; assert d["recipientSetupAvailable"] is False; assert d["eligible"] is False; assert float(d["availableBalance"]) == 50.0' <<< "$ELIGIBILITY"

ONBOARDING=$(curl --fail --silent --show-error \
  -H "Authorization: Bearer $USER_TOKEN" "$api/wallet/payouts/onboarding/status")
python3 -c 'import json,sys; d=json.load(sys.stdin); assert d["available"] is False; assert d["accountCreated"] is False; assert d["readyForPayout"] is False' <<< "$ONBOARDING"

STATUS=$(curl --silent --show-error -o /tmp/payout-unverified.json -w '%{http_code}' \
  -H "Authorization: Bearer $USER_TOKEN" \
  -H 'Content-Type: application/json' \
  -d '{"amount":10.00,"requestId":"smoke-unverified-001"}' \
  "$api/wallet/payouts")
test "$STATUS" = "403"
echo 'KYC and disabled Connect onboarding gates: OK'

curl --fail --silent --show-error -X POST \
  -H "Authorization: Bearer $USER_TOKEN" \
  "$api/verification/request" > /tmp/payout-verification.json
VERIFICATION_ID=$(json_value /tmp/payout-verification.json id)

curl --fail --silent --show-error -X PATCH \
  -H "Authorization: Bearer $ADMIN_TOKEN" \
  -H 'Content-Type: application/json' \
  -d '{"decision":"APPROVE","reason":null}' \
  "$api/admin/verifications/$VERIFICATION_ID" > /tmp/payout-verification-approved.json
python3 -c 'import json; assert json.load(open("/tmp/payout-verification-approved.json"))["status"] == "VERIFIED"'

ELIGIBILITY=$(curl --fail --silent --show-error \
  -H "Authorization: Bearer $USER_TOKEN" "$api/wallet/payouts/eligibility")
python3 -c 'import json,sys; d=json.load(sys.stdin); assert d["identityVerified"] is True; assert d["recipientSetupAvailable"] is False; assert d["eligible"] is True; assert float(d["minimumAmount"]) == 1.0' <<< "$ELIGIBILITY"
echo 'Verified payout eligibility: OK'

CANCELLED_REQUEST=$(curl --fail --silent --show-error \
  -H "Authorization: Bearer $USER_TOKEN" \
  -H 'Content-Type: application/json' \
  -d '{"amount":5.00,"requestId":"smoke-cancel-001"}' \
  "$api/wallet/payouts")
printf '%s' "$CANCELLED_REQUEST" > /tmp/payout-cancel-request.json
CANCEL_ID=$(json_value /tmp/payout-cancel-request.json id)

# The dedicated smoke uses a 15s dispatch interval so this public API cancellation path is deterministic.
curl --fail --silent --show-error -X POST \
  -H "Authorization: Bearer $USER_TOKEN" \
  "$api/wallet/payouts/$CANCEL_ID/cancel" > /tmp/payout-cancelled.json
python3 -c 'import json; d=json.load(open("/tmp/payout-cancelled.json")); assert d["status"] == "CANCELLED"; assert d["cancellable"] is False'

CANCEL_BALANCE=$(docker compose exec -T db psql -U dofast -d dofast -tAc \
  "SELECT balance FROM wallets WHERE user_id=$USER_ID;" | tr -d '[:space:]')
test "$CANCEL_BALANCE" = "50.00"
CANCEL_LEDGER=$(docker compose exec -T db psql -U dofast -d dofast -tAc \
  "SELECT string_agg(type || ':' || amount, ',' ORDER BY id) FROM wallet_transactions WHERE wallet_id=(SELECT id FROM wallets WHERE user_id=$USER_ID) AND type IN ('PAYOUT_RESERVE','PAYOUT_RESTORE');" | tr -d '[:space:]')
test "$CANCEL_LEDGER" = "PAYOUT_RESERVE:-5.00,PAYOUT_RESTORE:5.00"
echo 'Queued cancellation restores reserved funds exactly once: OK'

PAYOUT=$(curl --fail --silent --show-error \
  -H "Authorization: Bearer $USER_TOKEN" \
  -H 'Content-Type: application/json' \
  -d '{"amount":12.00,"requestId":"smoke-success-001"}' \
  "$api/wallet/payouts")
printf '%s' "$PAYOUT" > /tmp/payout-success.json
PAYOUT_ID=$(json_value /tmp/payout-success.json id)

RESERVED_BALANCE=$(docker compose exec -T db psql -U dofast -d dofast -tAc \
  "SELECT balance FROM wallets WHERE user_id=$USER_ID;" | tr -d '[:space:]')
test "$RESERVED_BALANCE" = "38.00"

# Repeat the same client request id. The operation must be idempotent and must not reserve twice.
REPLAY=$(curl --fail --silent --show-error \
  -H "Authorization: Bearer $USER_TOKEN" \
  -H 'Content-Type: application/json' \
  -d '{"amount":12.00,"requestId":"smoke-success-001"}' \
  "$api/wallet/payouts")
REPLAY_ID=$(python3 -c 'import json,sys; print(json.load(sys.stdin)["id"])' <<< "$REPLAY")
test "$REPLAY_ID" = "$PAYOUT_ID"
REPLAY_BALANCE=$(docker compose exec -T db psql -U dofast -d dofast -tAc \
  "SELECT balance FROM wallets WHERE user_id=$USER_ID;" | tr -d '[:space:]')
test "$REPLAY_BALANCE" = "38.00"

PAID=''
for attempt in {1..25}; do
  HISTORY=$(curl --fail --silent --show-error \
    -H "Authorization: Bearer $USER_TOKEN" "$api/wallet/payouts")
  STATUS=$(python3 -c 'import json,sys; target=int(sys.argv[1]); data=json.load(sys.stdin); print(next(item["status"] for item in data if item["id"] == target))' "$PAYOUT_ID" <<< "$HISTORY")
  if test "$STATUS" = "PAID"; then
    PAID='yes'
    break
  fi
  sleep 1
done
test "$PAID" = "yes"

PROVIDER_REFERENCE=$(docker compose exec -T db psql -U dofast -d dofast -tAc \
  "SELECT provider_reference FROM payout_requests WHERE id=$PAYOUT_ID;" | tr -d '[:space:]')
test "$PROVIDER_REFERENCE" = "sandbox-payout-$PAYOUT_ID"
PROVIDER_TRANSFER_REFERENCE=$(docker compose exec -T db psql -U dofast -d dofast -tAc \
  "SELECT COALESCE(provider_transfer_reference, '') FROM payout_requests WHERE id=$PAYOUT_ID;" | tr -d '[:space:]')
test -z "$PROVIDER_TRANSFER_REFERENCE"
PROVIDER_SUBMITTED_AT=$(docker compose exec -T db psql -U dofast -d dofast -tAc \
  "SELECT COALESCE(provider_submitted_at::text, '') FROM payout_requests WHERE id=$PAYOUT_ID;" | tr -d '[:space:]')
test -z "$PROVIDER_SUBMITTED_AT"

PAID_BALANCE=$(docker compose exec -T db psql -U dofast -d dofast -tAc \
  "SELECT balance FROM wallets WHERE user_id=$USER_ID;" | tr -d '[:space:]')
test "$PAID_BALANCE" = "38.00"

WITHDRAWABLE_REMAINING=$(docker compose exec -T db psql -U dofast -d dofast -tAc \
  "SELECT COALESCE(sum(remaining_amount),0)::numeric(19,2)::text FROM wallet_funding_lots WHERE wallet_id=(SELECT id FROM wallets WHERE user_id=$USER_ID) AND withdrawable=TRUE;" | tr -d '[:space:]')
test "$WITHDRAWABLE_REMAINING" = "38.00"
NON_WITHDRAWABLE_REMAINING=$(docker compose exec -T db psql -U dofast -d dofast -tAc \
  "SELECT COALESCE(sum(remaining_amount),0)::numeric(19,2)::text FROM wallet_funding_lots WHERE wallet_id=(SELECT id FROM wallets WHERE user_id=$USER_ID) AND withdrawable=FALSE;" | tr -d '[:space:]')
test "$NON_WITHDRAWABLE_REMAINING" = "0.00"

SUCCESS_RESERVE_COUNT=$(docker compose exec -T db psql -U dofast -d dofast -tAc \
  "SELECT count(*) FROM wallet_transactions WHERE operation_key='payout:${USER_ID}:client:smoke-success-001:reserve';" | tr -d '[:space:]')
test "$SUCCESS_RESERVE_COUNT" = "1"

EVENTS=$(docker compose exec -T db psql -U dofast -d dofast -tAc \
  "SELECT string_agg(event_type, ',' ORDER BY id) FROM payout_events WHERE payout_id=$PAYOUT_ID;" | tr -d '[:space:]')
test "$EVENTS" = "REQUESTED,PROCESSING_STARTED,PAID"

echo 'Sandbox payout reservation, idempotency and synchronous terminal settlement: OK'
