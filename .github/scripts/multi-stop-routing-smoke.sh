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

# This smoke needs spendable fixture value only; it must not masquerade as card funding or earnings.
bash .github/scripts/seed-wallet-funding.sh \
  "$OWNER_ID" 50.00 PLATFORM_ADJUSTMENT \
  "smoke:multistop:funding:$OWNER_ID" \
  "smoke:multistop:seed:$OWNER_ID"

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
  -H "Authorization: Bearer $WORKER_TOKEN" "$api/jobs/$JOB_ID/tracking/checkpoint")
python3 -c 'import json,sys; d=json.load(sys.stdin); assert d["phase"]=="TO_STOP"; assert d["nextStopSequence"]==0; assert d["remainingDistanceMeters"]>0' <<< "$STOP_ONE"

STOP_TWO=$(curl --fail --silent --show-error -X POST \
  -H "Authorization: Bearer $WORKER_TOKEN" "$api/jobs/$JOB_ID/tracking/checkpoint")
python3 -c 'import json,sys; d=json.load(sys.stdin); assert d["phase"]=="TO_STOP"; assert d["nextStopSequence"]==1; assert d["remainingDistanceMeters"]>0' <<< "$STOP_TWO"

DESTINATION=$(curl --fail --silent --show-error -X POST \
  -H "Authorization: Bearer $WORKER_TOKEN" "$api/jobs/$JOB_ID/tracking/checkpoint")
python3 -c 'import json,sys; d=json.load(sys.stdin); assert d["phase"]=="TO_DESTINATION"; assert d["nextStopSequence"] is None; assert d["remainingDistanceMeters"]>0' <<< "$DESTINATION"

DB_STATE=$(docker compose exec -T db psql -U dofast -d dofast -tAc \
  "SELECT phase || '|' || COALESCE(next_stop_sequence::text, 'null') FROM job_live_tracking WHERE job_id=$JOB_ID;")
test "${DB_STATE//[[:space:]]/}" = "TO_DESTINATION|null"

echo 'Live tracking advances A -> stop 1 -> stop 2 -> B: OK'

curl --fail --silent --show-error --output /tmp/multistop-completion.json \
  -X POST -H "Authorization: Bearer $WORKER_TOKEN" "$api/jobs/$JOB_ID/completion"
curl --fail --silent --show-error --output /tmp/multistop-confirm.json \
  -X POST -H "Authorization: Bearer $OWNER_TOKEN" "$api/jobs/$JOB_ID/confirm"

TERMINAL_ROUTE_STATUS=$(curl --silent --output /tmp/multistop-terminal-route.json --write-out '%{http_code}' \
  -H "Authorization: Bearer $OWNER_TOKEN" "$api/jobs/$JOB_ID/route")
test "$TERMINAL_ROUTE_STATUS" = "403"

# Make this terminal fixture older than the CI retention window. The cleanup scheduler runs every 500ms
# in CI; production keeps its normal cadence and an explicitly configured retention period.
docker compose exec -T db psql -U dofast -d dofast -v ON_ERROR_STOP=1 -c \
  "UPDATE jobs SET completed_at = CURRENT_TIMESTAMP - INTERVAL '2 days' WHERE id = $JOB_ID AND status = 'DONE';" >/dev/null

PURGED='f'
for attempt in {1..30}; do
  PURGED=$(docker compose exec -T db psql -U dofast -d dofast -tAc \
    "SELECT exact_location_purged_at IS NOT NULL FROM jobs WHERE id=$JOB_ID;" | tr -d '[:space:]')
  if test "$PURGED" = "t"; then
    break
  fi
  sleep 0.5
done
test "$PURGED" = "t"

PURGE_STATE=$(docker compose exec -T db psql -U dofast -d dofast -tAc \
  "SELECT (location IS NULL)::int || '|' || (location_private_label IS NULL)::int || '|' || (destination_location IS NULL)::int || '|' || (destination_private_label IS NULL)::int || '|' || (route_encoded_polyline IS NULL)::int || '|' || (route_quote_id IS NULL)::int FROM jobs WHERE id=$JOB_ID;" | tr -d '[:space:]')
test "$PURGE_STATE" = "1|1|1|1|1|1"

PRIVATE_STOP_FIELDS=$(docker compose exec -T db psql -U dofast -d dofast -tAc \
  "SELECT count(*) FROM job_route_stops WHERE job_id=$JOB_ID AND (location IS NOT NULL OR private_label IS NOT NULL OR place_id IS NOT NULL);" | tr -d '[:space:]')
test "$PRIVATE_STOP_FIELDS" = "0"
PUBLIC_STOP_FIELDS=$(docker compose exec -T db psql -U dofast -d dofast -tAc \
  "SELECT count(*) FROM job_route_stops WHERE job_id=$JOB_ID AND public_label IS NOT NULL;" | tr -d '[:space:]')
test "$PUBLIC_STOP_FIELDS" = "2"

QUOTE_ROWS=$(docker compose exec -T db psql -U dofast -d dofast -tAc \
  "SELECT count(*) FROM route_quotes WHERE id='$QUOTE_ID'::uuid;" | tr -d '[:space:]')
test "$QUOTE_ROWS" = "0"

PRESERVED_HISTORY=$(docker compose exec -T db psql -U dofast -d dofast -tAc \
  "SELECT status || '|' || price::text || '|' || (location_label IS NOT NULL)::int || '|' || (destination_label IS NOT NULL)::int || '|' || (route_distance_meters IS NOT NULL)::int FROM jobs WHERE id=$JOB_ID;" | tr -d '[:space:]')
test "$PRESERVED_HISTORY" = "DONE|20.00|1|1|1"

echo 'Terminal retention purges exact multi-stop route data while preserving public/accounting history: OK'
