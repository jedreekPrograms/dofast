#!/usr/bin/env bash
set -euo pipefail
trap 'echo "Cancellation smoke failed at line $LINENO"' ERR

api='http://localhost:8080'

register_and_login() {
  local email="$1"
  local nickname="$2"
  local prefix="$3"

  curl --fail --silent --show-error \
    -H 'Content-Type: application/json' \
    -d "{\"email\":\"$email\",\"nickname\":\"$nickname\",\"password\":\"CancelPass123!\"}" \
    "$api/users" > "/tmp/${prefix}-register.json"

  curl --fail --silent --show-error \
    -H 'Content-Type: application/json' \
    -d "{\"email\":\"$email\",\"password\":\"CancelPass123!\"}" \
    "$api/users/login" > "/tmp/${prefix}-login.json"
}

register_and_login 'cancel-owner@example.com' 'cancelOwner' owner
register_and_login 'cancel-worker@example.com' 'cancelWorker' worker
register_and_login 'cancel-outsider@example.com' 'cancelOutsider' outsider

OWNER_ID=$(python3 -c 'import json; print(json.load(open("/tmp/owner-register.json"))["id"])')
OWNER_TOKEN=$(python3 -c 'import json; print(json.load(open("/tmp/owner-login.json"))["accessToken"])')
WORKER_ID=$(python3 -c 'import json; print(json.load(open("/tmp/worker-register.json"))["id"])')
WORKER_TOKEN=$(python3 -c 'import json; print(json.load(open("/tmp/worker-login.json"))["accessToken"])')
OUTSIDER_TOKEN=$(python3 -c 'import json; print(json.load(open("/tmp/outsider-login.json"))["accessToken"])')
CATEGORY_ID=$(docker compose exec -T db psql -U dofast -d dofast -tAc \
  "SELECT id FROM job_categories WHERE slug='mala-paczka' AND fulfillment_mode='POINT_TO_POINT' AND active=TRUE;" | tr -d '[:space:]')
test -n "$CATEGORY_ID"

bash .github/scripts/seed-wallet-funding.sh \
  "$OWNER_ID" 50.00 PLATFORM_ADJUSTMENT \
  "smoke:cancellation:owner:$OWNER_ID" \
  "smoke:cancellation:seed:$OWNER_ID"

QUOTE=$(curl --fail --silent --show-error \
  -H "Authorization: Bearer $OWNER_TOKEN" \
  -H 'Content-Type: application/json' \
  -d '{"origin":{"latitude":51.1128,"longitude":17.0601,"publicLabel":"Wrocław","privateLabel":"Cancellation origin"},"destination":{"latitude":51.1099,"longitude":17.0325,"publicLabel":"Wrocław","privateLabel":"Cancellation destination"}}' \
  "$api/routing/quotes")
QUOTE_ID=$(python3 -c 'import json,sys; print(json.load(sys.stdin)["id"])' <<< "$QUOTE")

JOB=$(curl --fail --silent --show-error \
  -H "Authorization: Bearer $OWNER_TOKEN" \
  -H 'Content-Type: application/json' \
  -d "{\"title\":\"Negotiated cancellation smoke\",\"description\":\"Verify mutual cancellation, escrow refund and tracking shutdown.\",\"price\":20.00,\"categoryId\":$CATEGORY_ID,\"routeQuoteId\":\"$QUOTE_ID\"}" \
  "$api/jobs")
JOB_ID=$(python3 -c 'import json,sys; d=json.load(sys.stdin); assert d["status"]=="OPEN"; print(d["id"])' <<< "$JOB")

BALANCE_AFTER_HOLD=$(docker compose exec -T db psql -U dofast -d dofast -tAc \
  "SELECT balance FROM wallets WHERE user_id=$OWNER_ID;")
test "${BALANCE_AFTER_HOLD//[[:space:]]/}" = "30.00"

curl --fail --silent --show-error --output /tmp/accepted.json -X POST \
  -H "Authorization: Bearer $WORKER_TOKEN" \
  "$api/jobs/$JOB_ID/accept"
python3 -c 'import json; d=json.load(open("/tmp/accepted.json")); assert d["status"]=="IN_PROGRESS"'

OUTSIDER_STATUS=$(curl --silent --show-error --output /tmp/outsider-cancellation.json --write-out '%{http_code}' \
  -H "Authorization: Bearer $OUTSIDER_TOKEN" \
  "$api/jobs/$JOB_ID/cancellation")
test "$OUTSIDER_STATUS" = "404"

REQUEST=$(curl --fail --silent --show-error \
  -H "Authorization: Bearer $OWNER_TOKEN" \
  -H 'Content-Type: application/json' \
  -d '{"reason":"Zmiana planów — obie strony uzgodniły przerwanie realizacji."}' \
  "$api/jobs/$JOB_ID/cancellation")
REQUEST_ID=$(python3 -c 'import json,sys; d=json.load(sys.stdin); assert d["status"]=="PENDING"; assert d["requestedById"]=='"$OWNER_ID"'; assert d["counterpartyId"]=='"$WORKER_ID"'; print(d["id"])' <<< "$REQUEST")

OUTSIDER_PENDING_STATUS=$(curl --silent --show-error --output /tmp/outsider-pending-cancellation.json --write-out '%{http_code}' \
  -H "Authorization: Bearer $OUTSIDER_TOKEN" \
  "$api/jobs/$JOB_ID/cancellation")
test "$OUTSIDER_PENDING_STATUS" = "404"

OUTSIDER_APPROVE_STATUS=$(curl --silent --show-error --output /tmp/outsider-approve-cancellation.json --write-out '%{http_code}' \
  -X POST -H "Authorization: Bearer $OUTSIDER_TOKEN" \
  "$api/jobs/$JOB_ID/cancellation/approve")
test "$OUTSIDER_APPROVE_STATUS" = "404"

SELF_APPROVE_STATUS=$(curl --silent --show-error --output /tmp/self-approve.json --write-out '%{http_code}' \
  -X POST -H "Authorization: Bearer $OWNER_TOKEN" \
  "$api/jobs/$JOB_ID/cancellation/approve")
test "$SELF_APPROVE_STATUS" = "403"

PENDING=$(curl --fail --silent --show-error \
  -H "Authorization: Bearer $WORKER_TOKEN" \
  "$api/jobs/$JOB_ID/cancellation")
python3 -c 'import json,sys; d=json.load(sys.stdin); assert d["id"]=='"$REQUEST_ID"'; assert d["status"]=="PENDING"' <<< "$PENDING"

APPROVED=$(curl --fail --silent --show-error \
  -X POST -H "Authorization: Bearer $WORKER_TOKEN" \
  "$api/jobs/$JOB_ID/cancellation/approve")
python3 -c 'import json,sys; d=json.load(sys.stdin); assert d["status"]=="APPROVED"; assert d["resolvedById"]=='"$WORKER_ID"'' <<< "$APPROVED"

FINAL_JOB=$(curl --fail --silent --show-error \
  -H "Authorization: Bearer $OWNER_TOKEN" \
  "$api/jobs/$JOB_ID")
python3 -c 'import json,sys; d=json.load(sys.stdin); assert d["status"]=="CANCELLED"; assert d["cancelledAt"] is not None' <<< "$FINAL_JOB"

FINAL_BALANCE=$(docker compose exec -T db psql -U dofast -d dofast -tAc \
  "SELECT balance FROM wallets WHERE user_id=$OWNER_ID;")
test "${FINAL_BALANCE//[[:space:]]/}" = "50.00"

ESCROW=$(docker compose exec -T db psql -U dofast -d dofast -tAc \
  "SELECT status || '|' || (payee_id IS NULL) || '|' || (resolved_at IS NOT NULL) FROM escrow_transactions WHERE job_id=$JOB_ID;")
test "${ESCROW//[[:space:]]/}" = "REFUNDED|true|true"

REFUND_LEDGER=$(docker compose exec -T db psql -U dofast -d dofast -tAc \
  "SELECT COUNT(*) FROM wallet_transactions WHERE wallet_id=(SELECT id FROM wallets WHERE user_id=$OWNER_ID) AND operation_key='escrow:${JOB_ID}:refund' AND type='REFUND' AND amount=20.00 AND balance_after=50.00;")
test "${REFUND_LEDGER//[[:space:]]/}" = "1"

FUNDING_SOURCE=$(docker compose exec -T db psql -U dofast -d dofast -tAc \
  "SELECT source_type || '|' || remaining_amount::text || '|' || withdrawable FROM wallet_funding_lots WHERE wallet_id=(SELECT id FROM wallets WHERE user_id=$OWNER_ID);" | tr -d '[:space:]')
test "$FUNDING_SOURCE" = "PLATFORM_ADJUSTMENT|50.00|false"

CANCELLATION_ROW=$(docker compose exec -T db psql -U dofast -d dofast -tAc \
  "SELECT status || '|' || resolved_by_id || '|' || (resolved_at IS NOT NULL) FROM job_cancellation_requests WHERE id=$REQUEST_ID;")
test "${CANCELLATION_ROW//[[:space:]]/}" = "APPROVED|${WORKER_ID}|true"

TRACKING=$(docker compose exec -T db psql -U dofast -d dofast -tAc \
  "SELECT (current_location IS NULL) || '|' || (captured_at IS NULL) || '|' || (sharing_stopped_at IS NOT NULL) FROM job_live_tracking WHERE job_id=$JOB_ID;")
test "${TRACKING//[[:space:]]/}" = "true|true|true"

POST_CANCEL_PENDING_STATUS=$(curl --silent --show-error --output /tmp/no-pending.json --write-out '%{http_code}' \
  -H "Authorization: Bearer $OWNER_TOKEN" \
  "$api/jobs/$JOB_ID/cancellation")
test "$POST_CANCEL_PENDING_STATUS" = "204"

echo "Negotiated cancellation, neutral outsider privacy, escrow refund, funding restoration and tracking shutdown: OK"
