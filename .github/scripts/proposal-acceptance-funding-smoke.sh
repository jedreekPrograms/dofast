#!/usr/bin/env bash
set -euo pipefail
trap 'echo "Proposal acceptance funding smoke failed at line $LINENO"' ERR

api='http://localhost:8080'

register_and_login() {
  local email="$1"
  local nickname="$2"
  local prefix="$3"

  curl --fail --silent --show-error \
    -H 'Content-Type: application/json' \
    -d "{\"email\":\"$email\",\"nickname\":\"$nickname\",\"password\":\"ProposalFundingPass123!\"}" \
    "$api/users" > "/tmp/${prefix}-register.json"

  curl --fail --silent --show-error \
    -H 'Content-Type: application/json' \
    -d "{\"email\":\"$email\",\"password\":\"ProposalFundingPass123!\"}" \
    "$api/users/login" > "/tmp/${prefix}-login.json"
}

json_value() {
  local file="$1"
  local expression="$2"
  python3 - "$file" "$expression" <<'PY'
import json,sys
with open(sys.argv[1]) as fh:
    value=json.load(fh)
for part in sys.argv[2].split('.'):
    value=value[int(part)] if part.isdigit() else value[part]
print(value)
PY
}

register_and_login 'proposal-funding-owner@example.com' 'proposalFundingOwner' proposal-funding-owner
register_and_login 'proposal-funding-worker@example.com' 'proposalFundingWorker' proposal-funding-worker
register_and_login 'proposal-funding-outsider@example.com' 'proposalFundingOutsider' proposal-funding-outsider

OWNER_ID=$(json_value /tmp/proposal-funding-owner-register.json id)
WORKER_ID=$(json_value /tmp/proposal-funding-worker-register.json id)
OWNER_TOKEN=$(json_value /tmp/proposal-funding-owner-login.json accessToken)
WORKER_TOKEN=$(json_value /tmp/proposal-funding-worker-login.json accessToken)
OUTSIDER_TOKEN=$(json_value /tmp/proposal-funding-outsider-login.json accessToken)
CATEGORY_ID=$(docker compose exec -T db psql -U dofast -d dofast -tAc \
  "SELECT id FROM job_categories WHERE slug='montaz-mebli' AND fulfillment_mode='ON_SITE' AND active=TRUE;" | tr -d '[:space:]')
test -n "$CATEGORY_ID"

bash .github/scripts/seed-wallet-funding.sh \
  "$OWNER_ID" 35.00 PLATFORM_ADJUSTMENT \
  "smoke:proposal-funding:base:$OWNER_ID" \
  "smoke:proposal-funding:seed:$OWNER_ID"

JOB=$(curl --fail --silent --show-error \
  -H "Authorization: Bearer $OWNER_TOKEN" \
  -H 'Content-Type: application/json' \
  -d "{\"title\":\"Proposal funding smoke\",\"description\":\"Verify a higher negotiated offer can be funded safely before assignment.\",\"price\":30.00,\"categoryId\":$CATEGORY_ID,\"assignmentMode\":\"PROPOSALS\",\"priceNegotiationEnabled\":true,\"location\":{\"latitude\":51.0901,\"longitude\":17.0152,\"publicLabel\":\"Wrocław, Krzyki\",\"privateLabel\":\"ul. Testowa 42, lokal 7\",\"placeId\":\"proposal-funding-smoke\"}}" \
  "$api/jobs")
printf '%s' "$JOB" > /tmp/proposal-funding-job.json
JOB_ID=$(json_value /tmp/proposal-funding-job.json id)
python3 -c 'import json,sys; d=json.load(sys.stdin); assert d["status"]=="OPEN"; assert d["assignmentMode"]=="PROPOSALS"; assert d["priceNegotiationEnabled"] is True; assert float(d["price"])==30.0' <<< "$JOB"

PROPOSAL=$(curl --fail --silent --show-error \
  -H "Authorization: Bearer $WORKER_TOKEN" \
  -H 'Content-Type: application/json' \
  -d '{"amount":42.00,"message":"Mogę wykonać zlecenie za 42 zł."}' \
  "$api/jobs/$JOB_ID/proposals")
printf '%s' "$PROPOSAL" > /tmp/proposal-funding-proposal.json
PROPOSAL_ID=$(json_value /tmp/proposal-funding-proposal.json id)
python3 -c 'import json,sys; d=json.load(sys.stdin); assert d["status"]=="SUBMITTED"; assert float(d["amount"])==42.0' <<< "$PROPOSAL"

WORKER_QUOTE_STATUS=$(curl --silent --show-error --output /tmp/proposal-funding-worker-quote.json --write-out '%{http_code}' \
  -H "Authorization: Bearer $WORKER_TOKEN" \
  "$api/jobs/$JOB_ID/proposals/$PROPOSAL_ID/acceptance-funding")
test "$WORKER_QUOTE_STATUS" = "403"

OUTSIDER_QUOTE_STATUS=$(curl --silent --show-error --output /tmp/proposal-funding-outsider-quote.json --write-out '%{http_code}' \
  -H "Authorization: Bearer $OUTSIDER_TOKEN" \
  "$api/jobs/$JOB_ID/proposals/$PROPOSAL_ID/acceptance-funding")
test "$OUTSIDER_QUOTE_STATUS" = "403"

LEDGER_BEFORE=$(docker compose exec -T db psql -U dofast -d dofast -tAc \
  "SELECT count(*) FROM wallet_transactions wt JOIN wallets w ON w.id=wt.wallet_id WHERE w.user_id=$OWNER_ID;" | tr -d '[:space:]')

FUNDING=$(curl --fail --silent --show-error \
  -H "Authorization: Bearer $OWNER_TOKEN" \
  "$api/jobs/$JOB_ID/proposals/$PROPOSAL_ID/acceptance-funding")
python3 -c 'import json,sys; d=json.load(sys.stdin); assert float(d["currentEscrowAmount"])==30.0; assert float(d["targetEscrowAmount"])==42.0; assert float(d["walletContributionAvailable"])==5.0; assert float(d["paymentShortfall"])==7.0; assert float(d["stripeChargeAmount"])==7.0; assert d["currency"]=="PLN"; assert d["paymentRequired"] is True; assert d["onlinePaymentAvailable"] is True; assert "clientSecret" not in d; assert "walletBalance" not in d' <<< "$FUNDING"

LEDGER_AFTER_QUOTE=$(docker compose exec -T db psql -U dofast -d dofast -tAc \
  "SELECT count(*) FROM wallet_transactions wt JOIN wallets w ON w.id=wt.wallet_id WHERE w.user_id=$OWNER_ID;" | tr -d '[:space:]')
test "$LEDGER_BEFORE" = "$LEDGER_AFTER_QUOTE"

echo 'Requester-only funding quote is privacy-safe and read-only: OK'

EARLY_ACCEPT_STATUS=$(curl --silent --show-error --output /tmp/proposal-funding-early-accept.json --write-out '%{http_code}' \
  -X POST -H "Authorization: Bearer $OWNER_TOKEN" \
  "$api/jobs/$JOB_ID/proposals/$PROPOSAL_ID/accept")
test "$EARLY_ACCEPT_STATUS" = "400"

EARLY_STATE=$(docker compose exec -T db psql -U dofast -d dofast -tAc \
  "SELECT j.status || ':' || p.status || ':' || e.status || ':' || e.amount::text FROM jobs j JOIN job_proposals p ON p.job_id=j.id JOIN escrow_transactions e ON e.job_id=j.id WHERE j.id=$JOB_ID AND p.id=$PROPOSAL_ID;" | tr -d '[:space:]')
test "$EARLY_STATE" = "OPEN:SUBMITTED:HELD:30.00"

echo 'Insufficient acceptance rolls back without selecting worker: OK'

# Simulate the already-covered signed Stripe TOP_UP result. The proposal flow must consume
# only the exact escrow delta after the wallet credit becomes visible.
bash .github/scripts/seed-wallet-funding.sh \
  "$OWNER_ID" 7.00 STRIPE_PAYMENT \
  "pi_proposal_funding_smoke_$OWNER_ID" \
  "smoke:proposal-funding:stripe-credit:$OWNER_ID"

FUNDED=$(curl --fail --silent --show-error \
  -H "Authorization: Bearer $OWNER_TOKEN" \
  "$api/jobs/$JOB_ID/proposals/$PROPOSAL_ID/acceptance-funding")
python3 -c 'import json,sys; d=json.load(sys.stdin); assert float(d["currentEscrowAmount"])==30.0; assert float(d["targetEscrowAmount"])==42.0; assert float(d["walletContributionAvailable"])==12.0; assert float(d["paymentShortfall"])==0.0; assert float(d["stripeChargeAmount"])==0.0; assert d["paymentRequired"] is False; assert d["onlinePaymentAvailable"] is True' <<< "$FUNDED"

ACCEPTED=$(curl --fail --silent --show-error -X POST \
  -H "Authorization: Bearer $OWNER_TOKEN" \
  "$api/jobs/$JOB_ID/proposals/$PROPOSAL_ID/accept")
printf '%s' "$ACCEPTED" > /tmp/proposal-funding-accepted.json
python3 -c 'import json,sys; d=json.load(sys.stdin); assert d["job"]["status"]=="IN_PROGRESS"; assert float(d["job"]["price"])==42.0; assert d["job"]["takenById"]==int(sys.argv[1]); assert d["proposal"]["status"]=="ACCEPTED"' "$WORKER_ID" <<< "$ACCEPTED"

FINAL_BALANCE=$(docker compose exec -T db psql -U dofast -d dofast -tAc \
  "SELECT balance::text FROM wallets WHERE user_id=$OWNER_ID;" | tr -d '[:space:]')
test "$FINAL_BALANCE" = "0.00"

FINAL_ESCROW=$(docker compose exec -T db psql -U dofast -d dofast -tAc \
  "SELECT status || ':' || amount::text FROM escrow_transactions WHERE job_id=$JOB_ID;" | tr -d '[:space:]')
test "$FINAL_ESCROW" = "HELD:42.00"

ADJUSTMENT=$(docker compose exec -T db psql -U dofast -d dofast -tAc \
  "SELECT type || ':' || amount::text || ':' || balance_after::text FROM wallet_transactions WHERE operation_key='escrow:$JOB_ID:proposal:$PROPOSAL_ID:adjust:lock';" | tr -d '[:space:]')
test "$ADJUSTMENT" = "ESCROW_ADJUSTMENT_LOCK:-12.00:0.00"

FUNDING_REMAINING=$(docker compose exec -T db psql -U dofast -d dofast -tAc \
  "SELECT COALESCE(sum(remaining_amount), 0)::text FROM wallet_funding_lots WHERE wallet_id=(SELECT id FROM wallets WHERE user_id=$OWNER_ID);" | tr -d '[:space:]')
test "$FUNDING_REMAINING" = "0.00"
STRIPE_WITHDRAWABLE=$(docker compose exec -T db psql -U dofast -d dofast -tAc \
  "SELECT withdrawable FROM wallet_funding_lots WHERE wallet_id=(SELECT id FROM wallets WHERE user_id=$OWNER_ID) AND source_type='STRIPE_PAYMENT';" | tr -d '[:space:]')
test "$STRIPE_WITHDRAWABLE" = "f"

echo 'Funded higher proposal locks exact delta and assigns worker atomically: OK'
