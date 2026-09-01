#!/usr/bin/env bash
set -euo pipefail
trap 'echo "Runtime smoke assertion failed at line $LINENO"' ERR

api='http://localhost:8080'

register_and_login() {
  local email="$1"
  local nickname="$2"
  local password="$3"
  local prefix="$4"

  curl --fail --silent -H 'Content-Type: application/json' \
    -d "{\"email\":\"$email\",\"nickname\":\"$nickname\",\"password\":\"$password\"}" \
    "$api/users" > "/tmp/${prefix}-register.json"

  curl --fail --silent -H 'Content-Type: application/json' \
    -d "{\"email\":\"$email\",\"password\":\"$password\"}" \
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

POSTGIS_VERSION=$(docker compose exec -T db psql -U dofast -d dofast -tAc "SELECT extversion FROM pg_extension WHERE extname = 'postgis';")
test -n "${POSTGIS_VERSION//[[:space:]]/}"
PG_TRGM_VERSION=$(docker compose exec -T db psql -U dofast -d dofast -tAc "SELECT extversion FROM pg_extension WHERE extname = 'pg_trgm';")
test -n "${PG_TRGM_VERSION//[[:space:]]/}"
CATEGORY_ID=$(docker compose exec -T db psql -U dofast -d dofast -tAc "SELECT id FROM job_categories WHERE slug='mala-paczka' AND active=TRUE;")
CATEGORY_ID="${CATEGORY_ID//[[:space:]]/}"
test -n "$CATEGORY_ID"

register_and_login 'smoke@example.com' 'smokeuser' 'SmokePass123!' owner
register_and_login 'worker-smoke@example.com' 'smokeworker' 'WorkerPass123!' worker
register_and_login 'outsider-smoke@example.com' 'smokeoutsider' 'OutsiderPass123!' outsider

OWNER_ID=$(json_value /tmp/owner-register.json id)
OWNER_TOKEN=$(json_value /tmp/owner-login.json accessToken)
WORKER_ID=$(json_value /tmp/worker-register.json id)
WORKER_TOKEN=$(json_value /tmp/worker-login.json accessToken)
OUTSIDER_TOKEN=$(json_value /tmp/outsider-login.json accessToken)

test -n "$OWNER_TOKEN"
test -n "$WORKER_TOKEN"
test -n "$OUTSIDER_TOKEN"

echo "Register/login users: OK"

ADMIN_LOGIN=$(curl --fail --silent -H 'Content-Type: application/json' \
  -d '{"email":"admin-smoke@example.com","password":"AdminSmokePass123!"}' "$api/users/login")
ADMIN_TOKEN=$(python3 -c 'import json,sys; print(json.load(sys.stdin)["accessToken"])' <<< "$ADMIN_LOGIN")
echo "$ADMIN_LOGIN" | grep -q '"role":"ADMIN"'

USER_ADMIN_STATUS=$(curl --silent --output /tmp/user-admin.json --write-out '%{http_code}' \
  -H "Authorization: Bearer $OWNER_TOKEN" "$api/admin/users")
test "$USER_ADMIN_STATUS" = "403"

WALLET_COUNT=$(docker compose exec -T db psql -U dofast -d dofast -tAc \
  "SELECT COUNT(*) FROM wallets w JOIN users u ON u.id=w.user_id WHERE u.email IN ('smoke@example.com','worker-smoke@example.com','outsider-smoke@example.com');")
test "${WALLET_COUNT//[[:space:]]/}" = "3"

bash .github/scripts/seed-wallet-funding.sh \
  "$OWNER_ID" 100.00 PLATFORM_ADJUSTMENT \
  "smoke:runtime:owner:$OWNER_ID" \
  "smoke:runtime:seed:$OWNER_ID"

ORIGIN_PRIVATE='ul. Grunwaldzka 10, wejście A'
DESTINATION_PRIVATE='Rynek 1, wejście od placu'
ROUTE_QUOTE=$(curl --fail --silent -H "Authorization: Bearer $OWNER_TOKEN" -H 'Content-Type: application/json' \
  -d '{"origin":{"latitude":51.1128,"longitude":17.0601,"publicLabel":"Wrocław, Śródmieście","privateLabel":"ul. Grunwaldzka 10, wejście A","placeId":"origin-smoke"},"destination":{"latitude":51.1090,"longitude":17.0320,"publicLabel":"Wrocław, Stare Miasto","privateLabel":"Rynek 1, wejście od placu","placeId":"destination-smoke"}}' \
  "$api/routing/quotes")
echo "$ROUTE_QUOTE" > /tmp/route-quote.json
ROUTE_QUOTE_ID=$(json_value /tmp/route-quote.json id)
python3 -c 'import json,sys; d=json.load(sys.stdin); assert d["provider"]=="DETERMINISTIC_DEV"; assert d["distanceMeters"]>0; assert d["durationSeconds"]>0; assert d["origin"]["privateLabel"]; assert d["destination"]["privateLabel"]' <<< "$ROUTE_QUOTE"

OUTSIDER_QUOTE_STATUS=$(curl --silent --output /tmp/outsider-quote.json --write-out '%{http_code}' \
  -H "Authorization: Bearer $OUTSIDER_TOKEN" "$api/routing/quotes/$ROUTE_QUOTE_ID")
test "$OUTSIDER_QUOTE_STATUS" = "404"

OUTSIDER_MODE_ESTIMATES_STATUS=$(curl --silent --output /tmp/outsider-mode-estimates.json --write-out '%{http_code}' \
  -H "Authorization: Bearer $OUTSIDER_TOKEN" "$api/routing/quotes/$ROUTE_QUOTE_ID/mode-estimates")
test "$OUTSIDER_MODE_ESTIMATES_STATUS" = "404"

echo "Route quote ownership/estimate: OK"

JOB_RESPONSE=$(curl --fail --silent -H "Authorization: Bearer $OWNER_TOKEN" -H 'Content-Type: application/json' \
  -d "{\"title\":\"Smoke delivery\",\"description\":\"Deliver a small package from point A to point B.\",\"price\":25.00,\"categoryId\":$CATEGORY_ID,\"routeQuoteId\":\"$ROUTE_QUOTE_ID\"}" \
  "$api/jobs")
echo "$JOB_RESPONSE" > /tmp/job.json
JOB_ID=$(json_value /tmp/job.json id)
python3 -c 'import json,sys; d=json.load(sys.stdin); assert d["status"]=="OPEN"; assert d["locationLabel"]=="Wrocław, Śródmieście"; assert d["destinationLabel"]=="Wrocław, Stare Miasto"; assert d["routeDistanceMeters"]>0; assert d["routeDurationSeconds"]>0' <<< "$JOB_RESPONSE"
if echo "$JOB_RESPONSE" | grep -Fq "$ORIGIN_PRIVATE" || echo "$JOB_RESPONSE" | grep -Fq "$DESTINATION_PRIVATE"; then
  echo 'Create-job response leaked an exact route label'
  exit 1
fi
if echo "$JOB_RESPONSE" | grep -Eq '"latitude"|"longitude"'; then
  echo 'Create-job response leaked exact coordinates'
  exit 1
fi

REUSE_QUOTE_STATUS=$(curl --silent --output /tmp/reused-quote.json --write-out '%{http_code}' \
  -H "Authorization: Bearer $OWNER_TOKEN" -H 'Content-Type: application/json' \
  -d "{\"title\":\"Duplicate route\",\"description\":\"This must not reuse an already consumed quote.\",\"price\":10.00,\"categoryId\":$CATEGORY_ID,\"routeQuoteId\":\"$ROUTE_QUOTE_ID\"}" \
  "$api/jobs")
test "$REUSE_QUOTE_STATUS" = "409"

CONSUMED=$(docker compose exec -T db psql -U dofast -d dofast -tAc \
  "SELECT consumed_at IS NOT NULL FROM route_quotes WHERE id='$ROUTE_QUOTE_ID';")
test "${CONSUMED//[[:space:]]/}" = "t"

WALLET_BALANCE=$(docker compose exec -T db psql -U dofast -d dofast -tAc \
  "SELECT balance FROM wallets WHERE user_id=$OWNER_ID;")
test "${WALLET_BALANCE//[[:space:]]/}" = "75.00"

DISCOVERY=$(curl --fail --silent "$api/jobs?query=Stare%20Miasto&page=0&size=10")
python3 -c 'import json,sys; d=json.load(sys.stdin); assert d["totalElements"]==1; assert d["content"][0]["destinationLabel"]=="Wrocław, Stare Miasto"' <<< "$DISCOVERY"
if echo "$DISCOVERY" | grep -Fq "$ORIGIN_PRIVATE" || echo "$DISCOVERY" | grep -Fq "$DESTINATION_PRIVATE" || echo "$DISCOVERY" | grep -Eq '"latitude"|"longitude"'; then
  echo 'Public discovery leaked exact route details'
  exit 1
fi

NEARBY=$(curl --fail --silent "$api/jobs/nearby?latitude=51.1120&longitude=17.0610&radiusMeters=2000&limit=10")
echo "$NEARBY" | grep -q '"title":"Smoke delivery"'
if echo "$NEARBY" | grep -Eq '"latitude"|"longitude"'; then
  echo 'Nearby response leaked exact coordinates'
  exit 1
fi

WORKER_ROUTE_BEFORE=$(curl --silent --output /tmp/worker-route-before.json --write-out '%{http_code}' \
  -H "Authorization: Bearer $WORKER_TOKEN" "$api/jobs/$JOB_ID/route")
test "$WORKER_ROUTE_BEFORE" = "403"

OUTSIDER_ROUTE=$(curl --silent --output /tmp/outsider-route.json --write-out '%{http_code}' \
  -H "Authorization: Bearer $OUTSIDER_TOKEN" "$api/jobs/$JOB_ID/route")
test "$OUTSIDER_ROUTE" = "403"

ACCEPT_RESPONSE=$(curl --fail --silent -X POST -H "Authorization: Bearer $WORKER_TOKEN" "$api/jobs/$JOB_ID/accept")
echo "$ACCEPT_RESPONSE" | grep -q '"status":"IN_PROGRESS"'

WORKER_ROUTE=$(curl --fail --silent -H "Authorization: Bearer $WORKER_TOKEN" "$api/jobs/$JOB_ID/route")
python3 -c 'import json,sys; d=json.load(sys.stdin); assert d["origin"]["label"]=="ul. Grunwaldzka 10, wejście A"; assert d["destination"]["label"]=="Rynek 1, wejście od placu"; assert d["distanceMeters"]>0; assert d["durationSeconds"]>0' <<< "$WORKER_ROUTE"

echo "Job route privacy/lifecycle: OK"

INITIAL_TRACKING=$(curl --fail --silent -H "Authorization: Bearer $OWNER_TOKEN" "$api/jobs/$JOB_ID/tracking")
python3 -c 'import json,sys; d=json.load(sys.stdin); assert d["phase"]=="TO_ORIGIN"; assert d["workerId"]==int(sys.argv[1]); assert d["sharingActive"] is False; assert d["location"] is None' "$WORKER_ID" <<< "$INITIAL_TRACKING"

OUTSIDER_TRACKING_STATUS=$(curl --silent --output /tmp/outsider-tracking.json --write-out '%{http_code}' \
  -H "Authorization: Bearer $OUTSIDER_TOKEN" "$api/jobs/$JOB_ID/tracking")
test "$OUTSIDER_TRACKING_STATUS" = "403"

GPS_AT=$(utc_now)
OWNER_GPS_STATUS=$(curl --silent --output /tmp/owner-gps.json --write-out '%{http_code}' \
  -X PUT -H "Authorization: Bearer $OWNER_TOKEN" -H 'Content-Type: application/json' \
  -d "{\"latitude\":51.1200,\"longitude\":17.0700,\"accuracyMeters\":7.5,\"headingDegrees\":180.0,\"speedMetersPerSecond\":6.0,\"capturedAt\":\"$GPS_AT\"}" \
  "$api/jobs/$JOB_ID/tracking/location")
test "$OWNER_GPS_STATUS" = "403"

WORKER_GPS=$(curl --fail --silent -X PUT -H "Authorization: Bearer $WORKER_TOKEN" -H 'Content-Type: application/json' \
  -d "{\"latitude\":51.1200,\"longitude\":17.0700,\"accuracyMeters\":7.5,\"headingDegrees\":180.0,\"speedMetersPerSecond\":6.0,\"capturedAt\":\"$GPS_AT\"}" \
  "$api/jobs/$JOB_ID/tracking/location")
python3 -c 'import json,sys; d=json.load(sys.stdin); assert d["phase"]=="TO_ORIGIN"; assert d["sharingActive"] is True; assert abs(d["location"]["latitude"]-51.12)<1e-6; assert d["remainingDistanceMeters"]>0; assert d["remainingDurationSeconds"]>0' <<< "$WORKER_GPS"

DB_TRACKING_PRESENT=$(docker compose exec -T db psql -U dofast -d dofast -tAc \
  "SELECT current_location IS NOT NULL AND remaining_distance_meters IS NOT NULL FROM job_live_tracking WHERE job_id=$JOB_ID;")
test "${DB_TRACKING_PRESENT//[[:space:]]/}" = "t"

OWNER_TRACKING=$(curl --fail --silent -H "Authorization: Bearer $OWNER_TOKEN" "$api/jobs/$JOB_ID/tracking")
python3 -c 'import json,sys; d=json.load(sys.stdin); assert d["sharingActive"] is True; assert abs(d["location"]["longitude"]-17.07)<1e-6; assert d["remainingDistanceMeters"]>0' <<< "$OWNER_TRACKING"

PICKUP=$(curl --fail --silent -X POST -H "Authorization: Bearer $WORKER_TOKEN" "$api/jobs/$JOB_ID/tracking/pickup")
python3 -c 'import json,sys; d=json.load(sys.stdin); assert d["phase"]=="TO_DESTINATION"; assert d["sharingActive"] is True; assert d["remainingDistanceMeters"]>0; assert d["remainingDurationSeconds"]>0' <<< "$PICKUP"

sleep 0.1
GPS_AT_2=$(utc_now)
WORKER_GPS_2=$(curl --fail --silent -X PUT -H "Authorization: Bearer $WORKER_TOKEN" -H 'Content-Type: application/json' \
  -d "{\"latitude\":51.1150,\"longitude\":17.0500,\"accuracyMeters\":6.0,\"headingDegrees\":240.0,\"speedMetersPerSecond\":7.0,\"capturedAt\":\"$GPS_AT_2\"}" \
  "$api/jobs/$JOB_ID/tracking/location")
python3 -c 'import json,sys; d=json.load(sys.stdin); assert d["phase"]=="TO_DESTINATION"; assert d["sharingActive"] is True; assert abs(d["location"]["latitude"]-51.115)<1e-6; assert d["remainingDistanceMeters"]>0; assert d["remainingDurationSeconds"]>0' <<< "$WORKER_GPS_2"

echo "Live courier tracking A-to-B/privacy/ETA: OK"

CHAT_CLIENT_ID='11111111-2222-4333-8444-555555555555'
CHAT=$(curl --fail --silent -H "Authorization: Bearer $OWNER_TOKEN" -H 'Content-Type: application/json' \
  -d "{\"content\":\"Smoke route chat evidence\",\"clientMessageId\":\"$CHAT_CLIENT_ID\"}" \
  "$api/chat/jobs/$JOB_ID/messages")
echo "$CHAT" > /tmp/chat.json
CHAT_ID=$(json_value /tmp/chat.json id)
CHAT_SENDER=$(json_value /tmp/chat.json senderId)
test "$CHAT_SENDER" = "$OWNER_ID"

CHAT_RETRY=$(curl --fail --silent -H "Authorization: Bearer $OWNER_TOKEN" -H 'Content-Type: application/json' \
  -d "{\"content\":\"Smoke route chat evidence\",\"clientMessageId\":\"$CHAT_CLIENT_ID\"}" \
  "$api/chat/jobs/$JOB_ID/messages")
echo "$CHAT_RETRY" > /tmp/chat-retry.json
test "$(json_value /tmp/chat-retry.json id)" = "$CHAT_ID"

OUTSIDER_CHAT_STATUS=$(curl --silent --output /tmp/outsider-chat.json --write-out '%{http_code}' \
  -H "Authorization: Bearer $OUTSIDER_TOKEN" "$api/chat/jobs/$JOB_ID/messages?limit=20")
test "$OUTSIDER_CHAT_STATUS" = "404"

DISPUTE=$(curl --fail --silent -H "Authorization: Bearer $OWNER_TOKEN" -H 'Content-Type: application/json' \
  -d "{\"jobId\":$JOB_ID,\"reason\":\"NOT_COMPLETED\",\"description\":\"Smoke dispute for route job\"}" \
  "$api/disputes")
echo "$DISPUTE" > /tmp/dispute.json
DISPUTE_ID=$(python3 -c 'import json,sys; print(json.load(sys.stdin)["dispute"]["id"])' <<< "$DISPUTE")
echo "$DISPUTE" | grep -q '"status":"OPEN"'

TRACKING_CLEARED=$(docker compose exec -T db psql -U dofast -d dofast -tAc \
  "SELECT current_location IS NULL AND remaining_distance_meters IS NULL AND remaining_duration_seconds IS NULL AND sharing_stopped_at IS NOT NULL FROM job_live_tracking WHERE job_id=$JOB_ID;")
test "${TRACKING_CLEARED//[[:space:]]/}" = "t"

DISPUTED_TRACKING_STATUS=$(curl --silent --output /tmp/disputed-tracking.json --write-out '%{http_code}' \
  -H "Authorization: Bearer $OWNER_TOKEN" "$api/jobs/$JOB_ID/tracking")
test "$DISPUTED_TRACKING_STATUS" = "409"

ESCROW_HELD=$(docker compose exec -T db psql -U dofast -d dofast -tAc \
  "SELECT status FROM escrow_transactions WHERE job_id=$JOB_ID;")
test "${ESCROW_HELD//[[:space:]]/}" = "HELD"

ADMIN_QUEUE=$(curl --fail --silent -H "Authorization: Bearer $ADMIN_TOKEN" \
  "$api/admin/disputes?status=OPEN&page=0&size=20")
python3 -c 'import json,sys; d=json.load(sys.stdin); assert any(x["id"]==int(sys.argv[1]) for x in d["content"])' "$DISPUTE_ID" <<< "$ADMIN_QUEUE"

curl --fail --silent --output /tmp/claimed.json -X POST -H "Authorization: Bearer $ADMIN_TOKEN" \
  "$api/admin/disputes/$DISPUTE_ID/claim"

EVIDENCE=$(curl --fail --silent -H "Authorization: Bearer $ADMIN_TOKEN" \
  "$api/admin/disputes/$DISPUTE_ID/messages?limit=20")
echo "$EVIDENCE" | grep -q 'Smoke route chat evidence'

RESOLVED=$(curl --fail --silent -X POST -H "Authorization: Bearer $ADMIN_TOKEN" -H 'Content-Type: application/json' \
  -d '{"resolution":"REFUND_TO_REQUESTER","note":"Smoke test refund after route dispute"}' \
  "$api/admin/disputes/$DISPUTE_ID/resolve")
echo "$RESOLVED" | grep -q '"status":"RESOLVED"'

FINAL_JOB=$(curl --fail --silent -H "Authorization: Bearer $OWNER_TOKEN" "$api/jobs/$JOB_ID")
echo "$FINAL_JOB" | grep -q '"status":"CANCELLED"'

FINAL_BALANCE=$(docker compose exec -T db psql -U dofast -d dofast -tAc \
  "SELECT balance FROM wallets WHERE user_id=$OWNER_ID;")
test "${FINAL_BALANCE//[[:space:]]/}" = "100.00"

ESCROW_FINAL=$(docker compose exec -T db psql -U dofast -d dofast -tAc \
  "SELECT status FROM escrow_transactions WHERE job_id=$JOB_ID;")
test "${ESCROW_FINAL//[[:space:]]/}" = "REFUNDED"

WORKER_ROUTE_AFTER=$(curl --silent --output /tmp/worker-route-after.json --write-out '%{http_code}' \
  -H "Authorization: Bearer $WORKER_TOKEN" "$api/jobs/$JOB_ID/route")
test "$WORKER_ROUTE_AFTER" = "403"

echo "Chat/dispute/escrow regression: OK"
echo "Runtime smoke completed successfully"
