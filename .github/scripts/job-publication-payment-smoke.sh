#!/usr/bin/env bash
set -euo pipefail
trap 'echo "Job publication payment smoke failed at line $LINENO"' ERR

api='http://localhost:8080'

REGISTER=$(curl --fail --silent --show-error \
  -H 'Content-Type: application/json' \
  -d '{"email":"publication-smoke@example.com","nickname":"publicationSmoke","password":"PublicationPass123!"}' \
  "$api/users")
USER_ID=$(python3 -c 'import json,sys; print(json.load(sys.stdin)["id"])' <<< "$REGISTER")

LOGIN=$(curl --fail --silent --show-error \
  -H 'Content-Type: application/json' \
  -d '{"email":"publication-smoke@example.com","password":"PublicationPass123!"}' \
  "$api/users/login")
TOKEN=$(python3 -c 'import json,sys; print(json.load(sys.stdin)["accessToken"])' <<< "$LOGIN")

CATEGORY_ID=$(docker compose exec -T db psql -U dofast -d dofast -tAc \
  "SELECT id FROM job_categories WHERE slug='montaz-mebli' AND active=TRUE;")
CATEGORY_ID="${CATEGORY_ID//[[:space:]]/}"
test -n "$CATEGORY_ID"

WALLET_ID=$(docker compose exec -T db psql -U dofast -d dofast -tAc \
  "SELECT id FROM wallets WHERE user_id=$USER_ID;" | tr -d '[:space:]')
test -n "$WALLET_ID"

docker compose exec -T db psql -U dofast -d dofast -v ON_ERROR_STOP=1 <<SQL
BEGIN;
UPDATE wallets SET balance=25.00 WHERE id=$WALLET_ID;
INSERT INTO wallet_transactions (wallet_id, type, amount, job_id, created_at, operation_key, balance_after)
VALUES ($WALLET_ID, 'TOP_UP', 25.00, NULL, CURRENT_TIMESTAMP, 'smoke:job-publication:seed:25', 25.00);
COMMIT;
SQL

PARTIAL_PAYLOAD=$(cat <<JSON
{"requestId":"smoke-partial-001","job":{"title":"Publication partial smoke","description":"Payment-backed publication with a partial wallet balance.","price":70.00,"categoryId":$CATEGORY_ID,"location":{"latitude":51.1100,"longitude":17.0300,"publicLabel":"Wrocław","privateLabel":"ul. Testowa 1, Wrocław"}}}
JSON
)

PARTIAL=$(curl --fail --silent --show-error \
  -H "Authorization: Bearer $TOKEN" \
  -H 'Content-Type: application/json' \
  -d "$PARTIAL_PAYLOAD" \
  "$api/jobs/publications")
printf '%s' "$PARTIAL" > /tmp/publication-partial.json
PUBLICATION_ID=$(python3 -c 'import json; print(json.load(open("/tmp/publication-partial.json"))["id"])')
python3 - <<'PY'
import json
p=json.load(open('/tmp/publication-partial.json'))
assert p['status']=='PAYMENT_REQUIRED', p
assert float(p['totalAmount'])==70.0, p
assert float(p['walletReservedAmount'])==25.0, p
assert float(p['missingAmount'])==45.0, p
assert float(p['paymentAmount'])==45.0, p
assert p['paymentRequired'] is True, p
assert p['cancellable'] is True, p
assert p['jobId'] is None, p
PY

BALANCE=$(docker compose exec -T db psql -U dofast -d dofast -tAc \
  "SELECT balance FROM wallets WHERE id=$WALLET_ID;" | tr -d '[:space:]')
test "$BALANCE" = "0.00"

PUBLIC_JOB_COUNT=$(docker compose exec -T db psql -U dofast -d dofast -tAc \
  "SELECT count(*) FROM jobs WHERE created_by_id=$USER_ID AND title='Publication partial smoke';" | tr -d '[:space:]')
test "$PUBLIC_JOB_COUNT" = "0"

RESERVE_ROW=$(docker compose exec -T db psql -U dofast -d dofast -tAc \
  "SELECT type || ':' || amount || ':' || balance_after || ':' || operation_key FROM wallet_transactions WHERE operation_key='job-publication:${USER_ID}:smoke-partial-001:reserve';" | tr -d '[:space:]')
test "$RESERVE_ROW" = "JOB_PUBLICATION_RESERVE:-25.00:0.00:job-publication:${USER_ID}:smoke-partial-001:reserve"

REPLAY=$(curl --fail --silent --show-error \
  -H "Authorization: Bearer $TOKEN" \
  -H 'Content-Type: application/json' \
  -d "$PARTIAL_PAYLOAD" \
  "$api/jobs/publications")
REPLAY_ID=$(python3 -c 'import json,sys; print(json.load(sys.stdin)["id"])' <<< "$REPLAY")
test "$REPLAY_ID" = "$PUBLICATION_ID"
RESERVE_COUNT=$(docker compose exec -T db psql -U dofast -d dofast -tAc \
  "SELECT count(*) FROM wallet_transactions WHERE operation_key='job-publication:${USER_ID}:smoke-partial-001:reserve';" | tr -d '[:space:]')
test "$RESERVE_COUNT" = "1"
echo 'Partial wallet reservation and idempotent replay: OK'

CANCELLED=$(curl --fail --silent --show-error -X POST \
  -H "Authorization: Bearer $TOKEN" \
  "$api/jobs/publications/$PUBLICATION_ID/cancel")
printf '%s' "$CANCELLED" > /tmp/publication-cancelled.json
python3 -c 'import json; p=json.load(open("/tmp/publication-cancelled.json")); assert p["status"]=="CANCELLED" and p["cancellable"] is False'

RESTORED_BALANCE=$(docker compose exec -T db psql -U dofast -d dofast -tAc \
  "SELECT balance FROM wallets WHERE id=$WALLET_ID;" | tr -d '[:space:]')
test "$RESTORED_BALANCE" = "25.00"

RELEASE_COUNT=$(docker compose exec -T db psql -U dofast -d dofast -tAc \
  "SELECT count(*) FROM wallet_transactions WHERE operation_key='job-publication:${PUBLICATION_ID}:release' AND type='JOB_PUBLICATION_RELEASE' AND amount=25.00;" | tr -d '[:space:]')
test "$RELEASE_COUNT" = "1"

PRIVATE_PAYLOAD=$(docker compose exec -T db psql -U dofast -d dofast -tAc \
  "SELECT request_payload IS NULL FROM job_publications WHERE id=$PUBLICATION_ID;" | tr -d '[:space:]')
test "$PRIVATE_PAYLOAD" = "t"
echo 'Cancellation restores reserved balance exactly once and clears private payload: OK'

# Add only the missing 45 PLN to reach a fully-funded 70 PLN wallet and verify that
# publication immediately becomes a real OPEN job with one HELD escrow transaction.
docker compose exec -T db psql -U dofast -d dofast -v ON_ERROR_STOP=1 <<SQL
BEGIN;
UPDATE wallets SET balance=70.00 WHERE id=$WALLET_ID;
INSERT INTO wallet_transactions (wallet_id, type, amount, job_id, created_at, operation_key, balance_after)
VALUES ($WALLET_ID, 'TOP_UP', 45.00, NULL, CURRENT_TIMESTAMP, 'smoke:job-publication:seed:45', 70.00);
COMMIT;
SQL

FULL=$(curl --fail --silent --show-error \
  -H "Authorization: Bearer $TOKEN" \
  -H 'Content-Type: application/json' \
  -d "{\"requestId\":\"smoke-full-001\",\"job\":{\"title\":\"Publication fully funded smoke\",\"description\":\"Fully funded publication should create escrow immediately.\",\"price\":70.00,\"categoryId\":$CATEGORY_ID,\"location\":{\"latitude\":51.1110,\"longitude\":17.0310,\"publicLabel\":\"Wrocław\",\"privateLabel\":\"ul. Testowa 2, Wrocław\"}}}" \
  "$api/jobs/publications")
printf '%s' "$FULL" > /tmp/publication-full.json
JOB_ID=$(python3 -c 'import json; print(json.load(open("/tmp/publication-full.json"))["jobId"])')
python3 - <<'PY'
import json
p=json.load(open('/tmp/publication-full.json'))
assert p['status']=='PUBLISHED', p
assert float(p['walletReservedAmount'])==0.0, p
assert float(p['paymentAmount'])==0.0, p
assert p['paymentRequired'] is False, p
assert p['jobId'] is not None, p
PY

FINAL_BALANCE=$(docker compose exec -T db psql -U dofast -d dofast -tAc \
  "SELECT balance FROM wallets WHERE id=$WALLET_ID;" | tr -d '[:space:]')
test "$FINAL_BALANCE" = "0.00"

ESCROW=$(docker compose exec -T db psql -U dofast -d dofast -tAc \
  "SELECT status || ':' || amount || ':' || payer_id FROM escrow_transactions WHERE job_id=$JOB_ID;" | tr -d '[:space:]')
test "$ESCROW" = "HELD:70.00:${USER_ID}"

ESCROW_LOCK=$(docker compose exec -T db psql -U dofast -d dofast -tAc \
  "SELECT type || ':' || amount || ':' || balance_after FROM wallet_transactions WHERE operation_key='escrow:${JOB_ID}:lock';" | tr -d '[:space:]')
test "$ESCROW_LOCK" = "ESCROW_LOCK:-70.00:0.00"

PUBLIC_JOB=$(curl --fail --silent --show-error \
  -H "Authorization: Bearer $TOKEN" \
  "$api/jobs/$JOB_ID")
echo "$PUBLIC_JOB" | grep -q '"status":"OPEN"'
echo "$PUBLIC_JOB" | grep -q 'Publication fully funded smoke'

echo 'Fully funded wallet publication creates the public job and HELD escrow: OK'
