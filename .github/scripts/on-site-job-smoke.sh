#!/usr/bin/env bash
set -euo pipefail
trap 'echo "On-site job smoke failed at line $LINENO"' ERR

api='http://localhost:8080'

register_and_login() {
  local email="$1"
  local nickname="$2"
  local prefix="$3"

  curl --fail --silent --show-error \
    -H 'Content-Type: application/json' \
    -d "{\"email\":\"$email\",\"nickname\":\"$nickname\",\"password\":\"OnSitePass123!\"}" \
    "$api/users" > "/tmp/${prefix}-register.json"

  curl --fail --silent --show-error \
    -H 'Content-Type: application/json' \
    -d "{\"email\":\"$email\",\"password\":\"OnSitePass123!\"}" \
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

register_and_login 'onsite-owner@example.com' 'onsiteOwner' onsite-owner
register_and_login 'onsite-worker@example.com' 'onsiteWorker' onsite-worker

OWNER_ID=$(json_value /tmp/onsite-owner-register.json id)
OWNER_TOKEN=$(json_value /tmp/onsite-owner-login.json accessToken)
WORKER_TOKEN=$(json_value /tmp/onsite-worker-login.json accessToken)
CATEGORY_ID=$(docker compose exec -T db psql -U dofast -d dofast -tAc "SELECT id FROM job_categories WHERE slug='montaz-mebli' AND fulfillment_mode='ON_SITE' AND active=TRUE;" | tr -d '[:space:]')
test -n "$CATEGORY_ID"

docker compose exec -T db psql -U dofast -d dofast -v ON_ERROR_STOP=1 <<SQL
BEGIN;
UPDATE wallets SET balance = 120.00 WHERE user_id = $OWNER_ID;
INSERT INTO wallet_transactions (
    wallet_id, type, amount, job_id, created_at, operation_key, balance_after
)
SELECT id, 'TOP_UP', 120.00, NULL, CURRENT_TIMESTAMP, 'smoke:onsite:seed:' || id, 120.00
FROM wallets WHERE user_id = $OWNER_ID;
COMMIT;
SQL

PRIVATE_ADDRESS='ul. Powstańców Śląskich 100, mieszkanie 8'
JOB=$(curl --fail --silent --show-error \
  -H "Authorization: Bearer $OWNER_TOKEN" \
  -H 'Content-Type: application/json' \
  -d "{\"title\":\"Montaż szafy smoke\",\"description\":\"Zmontuj szafę w mieszkaniu i ustaw ją przy ścianie.\",\"price\":80.00,\"categoryId\":$CATEGORY_ID,\"location\":{\"latitude\":51.0901,\"longitude\":17.0152,\"publicLabel\":\"Wrocław, Krzyki\",\"privateLabel\":\"$PRIVATE_ADDRESS\",\"placeId\":\"onsite-smoke-place\"}}" \
  "$api/jobs")
printf '%s' "$JOB" > /tmp/onsite-job.json
JOB_ID=$(json_value /tmp/onsite-job.json id)
python3 -c 'import json,sys; d=json.load(sys.stdin); assert d["fulfillmentMode"]=="ON_SITE"; assert d["locationLabel"]=="Wrocław, Krzyki"; assert d["destinationLabel"] is None; assert d["routeDistanceMeters"] is None; assert d["routeDurationSeconds"] is None; assert sys.argv[1] not in sys.stdin.read()' "$PRIVATE_ADDRESS" <<< "$JOB"
if echo "$JOB" | grep -Fq "$PRIVATE_ADDRESS"; then
  echo 'Create-job response leaked exact on-site address'
  exit 1
fi

echo 'On-site create and public privacy: OK'

STATUS=$(curl --silent --output /tmp/onsite-before-location.json --write-out '%{http_code}' \
  -H "Authorization: Bearer $WORKER_TOKEN" "$api/jobs/$JOB_ID/location")
test "$STATUS" = "403"

echo 'Unassigned worker cannot read exact on-site address: OK'

ACCEPTED=$(curl --fail --silent --show-error -X POST \
  -H "Authorization: Bearer $WORKER_TOKEN" "$api/jobs/$JOB_ID/accept")
python3 -c 'import json,sys; d=json.load(sys.stdin); assert d["status"]=="IN_PROGRESS"; assert d["fulfillmentMode"]=="ON_SITE"' <<< "$ACCEPTED"

LOCATION=$(curl --fail --silent --show-error \
  -H "Authorization: Bearer $WORKER_TOKEN" "$api/jobs/$JOB_ID/location")
python3 -c 'import json,sys; d=json.load(sys.stdin); assert d["label"]==sys.argv[1]; assert abs(float(d["latitude"])-51.0901)<1e-6; assert abs(float(d["longitude"])-17.0152)<1e-6' "$PRIVATE_ADDRESS" <<< "$LOCATION"

TRACKING_ROWS=$(docker compose exec -T db psql -U dofast -d dofast -tAc "SELECT count(*) FROM job_live_tracking WHERE job_id=$JOB_ID;" | tr -d '[:space:]')
test "$TRACKING_ROWS" = "0"
TRACKING_STATUS=$(curl --silent --output /tmp/onsite-tracking.json --write-out '%{http_code}' \
  -H "Authorization: Bearer $WORKER_TOKEN" "$api/jobs/$JOB_ID/tracking")
test "$TRACKING_STATUS" = "404"

echo 'Assigned worker gets exact address without courier tracking: OK'

curl --fail --silent --show-error --output /tmp/onsite-completion.json -X POST \
  -H "Authorization: Bearer $WORKER_TOKEN" "$api/jobs/$JOB_ID/completion"
curl --fail --silent --show-error --output /tmp/onsite-confirm.json -X POST \
  -H "Authorization: Bearer $OWNER_TOKEN" "$api/jobs/$JOB_ID/confirm"

AFTER_STATUS=$(curl --silent --output /tmp/onsite-after-location.json --write-out '%{http_code}' \
  -H "Authorization: Bearer $WORKER_TOKEN" "$api/jobs/$JOB_ID/location")
test "$AFTER_STATUS" = "403"

echo 'Worker exact-address access closes after completion: OK'
