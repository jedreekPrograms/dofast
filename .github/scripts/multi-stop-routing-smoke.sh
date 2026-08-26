#!/usr/bin/env bash
set -euo pipefail
trap 'echo "Multi-stop smoke failed at line $LINENO"' ERR

api='http://localhost:8080'

register_and_login() {
  local email="$1"
  local nickname="$2"
  local prefix="$3"

  curl --fail --silent --show-error \
    -H 'Content-Type: application/json' \
    -d "{\"email\":\"$email\",\"nickname\":\"$nickname\",\"password\":\"MultiStopPass123!\"}" \
    "$api/users" > "/tmp/${prefix}-register.json"

  curl --fail --silent --show-error \
    -H 'Content-Type: application/json' \
    -d "{\"email\":\"$email\",\"password\":\"MultiStopPass123!\"}" \
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

utc_now() {
  python3 - <<'PY'
from datetime import datetime, timezone
print(datetime.now(timezone.utc).isoformat().replace('+00:00', 'Z'))
PY
}

register_and_login 'multistop-owner@example.com' 'multistopOwner' owner
register_and_login 'multistop-worker@example.com' 'multistopWorker' worker

OWNER_ID=$(json_value /tmp/owner-register.json id)
OWNER_TOKEN=$(json_value /tmp/owner-login.json accessToken)
WORKER_ID=$(json_value /tmp/worker-register.json id)
WORKER_TOKEN=$(json_value /tmp/worker-login.json accessToken)
CATEGORY_ID=$(docker compose exec -T db psql -U dofast -d dofast -tAc "SELECT id FROM job_categories WHERE slug='mala-paczka' AND active=TRUE;" | tr -d '[:space:]')
test -n "$CATEGORY_ID"

docker compose exec -T db psql -U dofast -d dofast -v ON_ERROR_STOP=1 <<SQL
BEGIN;
UPDATE wallets SET balance = 50.00 WHERE user_id = $OWNER_ID;
INSERT INTO wallet_transactions (
    wallet_id, type, amount, job_id, created_at, operation_key, balance_after
)
SELECT id, 'TOP_UP', 50.00, NULL, CURRENT_TIMESTAMP, 'smoke:multistop:seed:' || id, 50.00
FROM wallets WHERE user_id = $OWNER_ID;
COMMIT;
SQL

STOP_ONE_PRIVATE='ul. Piastowska 20, odbiór w recepcji'
STOP_TWO_PRIVATE='ul. Szczytnicka 30, wejście od podwórza'
QUOTE=$(curl --fail --silent --show-error \
  -H "Authorization: Bearer $OWNER_TOKEN" \
  -H 'Content-Type: application/json' \
  -d '{
    "origin":{"latitude":51.1128,"longitude":17.0601,"publicLabel":"Wrocław, Plac Grunwaldzki","privateLabel":"Start dokładny","placeId":"multi-origin"},
    "stops":[
      {"latitude":51.1140,"longitude":17.0520,"publicLabel":"Wrocław, Śródmieście","privateLabel":"ul. Piastowska 20, odbiór w recepcji","placeId":"multi-stop-1"},
      {"latitude":51.1130,"longitude":17.0430,"publicLabel":"Wrocław, Śródmieście","privateLabel":"ul. Szczytnicka 30, wejście od podwórza","placeId":"multi-stop-2"}
    ],
    "destination":{"latitude":51.1090,"longitude":17.0320,"publicLabel":"Wrocław, Stare Miasto","privateLabel":"Meta dokładna","placeId":"multi-destination"}
  }' \
  "$api/routing/quotes")
printf '%s' "$QUOTE" > /tmp/multistop-quote.json
QUOTE_ID=$(json_value /tmp/multistop-quote.json id)
python3 -c 'import json,sys; d=json.load(sys.stdin); assert len(d["stops"])==2; assert d["stops"][0]["privateLabel"]==sys.argv[1]; assert d["stops"][1]["privateLabel"]==sys.argv[2]; assert d["distanceMeters"]>0; assert d["durationSeconds"]>0' "$STOP_ONE_PRIVATE" "$STOP_TWO_PRIVATE" <<< "$QUOTE"

echo 'Multi-stop route quote: OK'

JOB=$(curl --fail --silent --show-error \
  -H "Authorization: Bearer $OWNER_TOKEN" \
  -H 'Content-Type: application/json' \
  -d "{\"title\":\"Multi-stop smoke\",\"description\":\"Pick up items at two ordered stops and deliver them to B.\",\"price\":20.00,\"categoryId\":$CATEGORY_ID,\"routeQuoteId\":\"$QUOTE_ID\"}" \
  "$api/jobs")
printf '%s' "$JOB" > /tmp/multistop-job.json
JOB_ID=$(json_value /tmp/multistop-job.json id)
if echo "$JOB" | grep -Fq "$STOP_ONE_PRIVATE" || echo "$JOB" | grep -Fq "$STOP_TWO_PRIVATE"; then
  echo 'Create-job response leaked exact intermediate stop labels'
  exit 1
fi

echo 'Multi-stop job privacy: OK'

curl --fail --silent --show-error --output /tmp/multistop-accept.json \
  -X POST -H "Authorization: Bearer $WORKER_TOKEN" "$api/jobs/$JOB_ID/accept"

ROUTE=$(curl --fail --silent --show-error \
  -H "Authorization: Bearer $WORKER_TOKEN" "$api/jobs/$JOB_ID/route")
python3 -c 'import json,sys; d=json.load(sys.stdin); assert len(d["stops"])==2; assert d["stops"][0]["label"]==sys.argv[1]; assert d["stops"][1]["label"]==sys.argv[2]' "$STOP_ONE_PRIVATE" "$STOP_TWO_PRIVATE" <<< "$ROUTE"

echo 'Assigned worker receives exact ordered stops: OK'

INITIAL=$(curl --fail --silent --show-error \
  -H "Authorization: Bearer $OWNER_TOKEN" "$api/jobs/$JOB_ID/tracking")
python3 -c 'import json,sys; d=json.load(sys.stdin); assert d["phase"]=="TO_ORIGIN"; assert d["nextStopSequence"] is None; assert d["workerId"]==int(sys.argv[1])' "$WORKER_ID" <<< "$INITIAL"

GPS_AT=$(utc_now)
curl --fail --silent --show-error --output /tmp/multistop-gps.json \
  -X PUT \
  -H "Authorization: Bearer $WORKER_TOKEN" \
  -H 'Content-Type: application/json' \
  -d "{\"latitude\":51.1128,\"longitude\":17.0601,\"accuracyMeters\":5.0,\"headingDegrees\":180.0,\"speedMetersPerSecond\":1.0,\"capturedAt\":\"$GPS_AT\"}" \
  "$api/jobs/$JOB_ID/tracking/location"

STOP_ONE=$(curl --fail --silent --show-error -X POST \
  -H "Authorization: Bearer $WORKER_TOKEN" "$api/jobs/$JOB_ID/tracking/pickup")
python3 -c 'import json,sys; d=json.load(sys.stdin); assert d["phase"]=="TO_STOP"; assert d["nextStopSequence"]==0; assert d["remainingDistanceMeters"]>0' <<< "$STOP_ONE"

STOP_TWO=$(curl --fail --silent --show-error -X POST \
  -H "Authorization: Bearer $WORKER_TOKEN" "$api/jobs/$JOB_ID/tracking/pickup")
python3 -c 'import json,sys; d=json.load(sys.stdin); assert d["phase"]=="TO_STOP"; assert d["nextStopSequence"]==1; assert d["remainingDistanceMeters"]>0' <<< "$STOP_TWO"

DESTINATION=$(curl --fail --silent --show-error -X POST \
  -H "Authorization: Bearer $WORKER_TOKEN" "$api/jobs/$JOB_ID/tracking/pickup")
python3 -c 'import json,sys; d=json.load(sys.stdin); assert d["phase"]=="TO_DESTINATION"; assert d["nextStopSequence"] is None; assert d["remainingDistanceMeters"]>0' <<< "$DESTINATION"

DB_STATE=$(docker compose exec -T db psql -U dofast -d dofast -tAc \
  "SELECT phase || '|' || COALESCE(next_stop_sequence::text, 'null') FROM job_live_tracking WHERE job_id=$JOB_ID;")
test "${DB_STATE//[[:space:]]/}" = "TO_DESTINATION|null"

echo 'Live tracking advances A -> stop 1 -> stop 2 -> B: OK'
