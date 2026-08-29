#!/usr/bin/env bash
set -euo pipefail
trap 'echo "Job publication minimum funding smoke failed at line $LINENO"' ERR

api='http://localhost:8080'

REGISTER=$(curl --fail --silent --show-error \
  -H 'Content-Type: application/json' \
  -d '{"email":"publication-minimum-smoke@example.com","nickname":"publicationMinimumSmoke","password":"PublicationPass123!"}' \
  "$api/users")
USER_ID=$(python3 -c 'import json,sys; print(json.load(sys.stdin)["id"])' <<< "$REGISTER")

LOGIN=$(curl --fail --silent --show-error \
  -H 'Content-Type: application/json' \
  -d '{"email":"publication-minimum-smoke@example.com","password":"PublicationPass123!"}' \
  "$api/users/login")
TOKEN=$(python3 -c 'import json,sys; print(json.load(sys.stdin)["accessToken"])' <<< "$LOGIN")

CATEGORY_ID=$(docker compose exec -T db psql -U dofast -d dofast -tAc \
  "SELECT id FROM job_categories WHERE slug='montaz-mebli' AND active=TRUE;" | tr -d '[:space:]')
WALLET_ID=$(docker compose exec -T db psql -U dofast -d dofast -tAc \
  "SELECT id FROM wallets WHERE user_id=$USER_ID;" | tr -d '[:space:]')
test -n "$CATEGORY_ID"
test -n "$WALLET_ID"

bash .github/scripts/seed-wallet-funding.sh \
  "$USER_ID" 25.00 PLATFORM_ADJUSTMENT \
  'smoke:job-publication:minimum:source' \
  'smoke:job-publication:minimum:seed'

PUBLICATION=$(curl --fail --silent --show-error \
  -H "Authorization: Bearer $TOKEN" \
  -H 'Content-Type: application/json' \
  -d "{\"requestId\":\"smoke-minimum-001\",\"job\":{\"title\":\"Publication minimum funding smoke\",\"description\":\"Verify exact funding when the Stripe minimum exceeds the wallet shortfall.\",\"price\":25.50,\"categoryId\":$CATEGORY_ID,\"location\":{\"latitude\":51.1100,\"longitude\":17.0300,\"publicLabel\":\"Wrocław\",\"privateLabel\":\"ul. Testowa 3, Wrocław\"}}}" \
  "$api/jobs/publications")
printf '%s' "$PUBLICATION" > /tmp/publication-minimum.json
PUBLICATION_ID=$(python3 -c 'import json; print(json.load(open("/tmp/publication-minimum.json"))["id"])')

python3 - <<'PY'
import json
p=json.load(open('/tmp/publication-minimum.json'))
assert p['status']=='PAYMENT_REQUIRED', p
assert float(p['totalAmount'])==25.50, p
assert float(p['walletReservedAmount'])==24.50, p
assert float(p['missingAmount'])==1.00, p
assert float(p['paymentAmount'])==1.00, p
assert round(float(p['walletReservedAmount']) + float(p['paymentAmount']), 2)==25.50, p
PY

BALANCE=$(docker compose exec -T db psql -U dofast -d dofast -tAc \
  "SELECT balance FROM wallets WHERE id=$WALLET_ID;" | tr -d '[:space:]')
test "$BALANCE" = "0.50"

RESERVE_ROW=$(docker compose exec -T db psql -U dofast -d dofast -tAc \
  "SELECT type || ':' || amount || ':' || balance_after FROM wallet_transactions WHERE operation_key='job-publication:${USER_ID}:smoke-minimum-001:reserve';" | tr -d '[:space:]')
test "$RESERVE_ROW" = "JOB_PUBLICATION_RESERVE:-24.50:0.50"

REMAINING_DURING_RESERVATION=$(docker compose exec -T db psql -U dofast -d dofast -tAc \
  "SELECT source_type || ':' || remaining_amount::text || ':' || withdrawable FROM wallet_funding_lots WHERE wallet_id=$WALLET_ID;" | tr -d '[:space:]')
test "$REMAINING_DURING_RESERVATION" = "PLATFORM_ADJUSTMENT:0.50:false"

curl --fail --silent --show-error -X POST \
  -H "Authorization: Bearer $TOKEN" \
  "$api/jobs/publications/$PUBLICATION_ID/cancel" >/tmp/publication-minimum-cancelled.json

RESTORED=$(docker compose exec -T db psql -U dofast -d dofast -tAc \
  "SELECT balance FROM wallets WHERE id=$WALLET_ID;" | tr -d '[:space:]')
test "$RESTORED" = "25.00"
RESTORED_SOURCE=$(docker compose exec -T db psql -U dofast -d dofast -tAc \
  "SELECT source_type || ':' || remaining_amount::text || ':' || withdrawable FROM wallet_funding_lots WHERE wallet_id=$WALLET_ID;" | tr -d '[:space:]')
test "$RESTORED_SOURCE" = "PLATFORM_ADJUSTMENT:25.00:false"

echo 'Stripe minimum funding keeps total funding exact, preserves unused wallet balance and restores the original source: OK'
