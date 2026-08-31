#!/usr/bin/env bash
set -euo pipefail
trap 'echo "Emergency account enforcement smoke failed at line $LINENO"' ERR

api='http://localhost:8080'
password='EmergencyPass123!'

json_value() {
  local file="$1"
  local expression="$2"
  python3 - "$file" "$expression" <<'PY'
import json,sys
with open(sys.argv[1], encoding='utf-8') as fh:
    value=json.load(fh)
for part in sys.argv[2].split('.'):
    value=value[int(part)] if part.isdigit() else value[part]
print(value)
PY
}

register_and_login() {
  local email="$1"
  local nickname="$2"
  local prefix="$3"
  curl --fail --silent -H 'Content-Type: application/json' \
    -d "{\"email\":\"$email\",\"nickname\":\"$nickname\",\"password\":\"$password\"}" \
    "$api/users" > "/tmp/${prefix}-register.json"
  curl --fail --silent -H 'Content-Type: application/json' \
    -d "{\"email\":\"$email\",\"password\":\"$password\"}" \
    "$api/users/login" > "/tmp/${prefix}-login.json"
}

register_and_login 'emergency-owner@example.com' 'emergencyOwner' owner
register_and_login 'emergency-worker@example.com' 'emergencyWorker' worker
register_and_login 'emergency-reporter@example.com' 'emergencyReporter' reporter

OWNER_ID=$(json_value /tmp/owner-register.json id)
OWNER_TOKEN=$(json_value /tmp/owner-login.json accessToken)
WORKER_ID=$(json_value /tmp/worker-register.json id)
WORKER_TOKEN=$(json_value /tmp/worker-login.json accessToken)
REPORTER_TOKEN=$(json_value /tmp/reporter-login.json accessToken)

ADMIN_LOGIN=$(curl --fail --silent -H 'Content-Type: application/json' \
  -d '{"email":"emergency-admin@example.com","password":"EmergencyAdmin123!"}' \
  "$api/users/login")
printf '%s' "$ADMIN_LOGIN" > /tmp/emergency-admin-login.json
ADMIN_TOKEN=$(json_value /tmp/emergency-admin-login.json accessToken)
ADMIN_ID=$(json_value /tmp/emergency-admin-login.json user.id)

test -n "$OWNER_TOKEN"
test -n "$WORKER_TOKEN"
test -n "$REPORTER_TOKEN"
test -n "$ADMIN_TOKEN"

bash .github/scripts/seed-wallet-funding.sh \
  "$OWNER_ID" 100.00 PLATFORM_ADJUSTMENT \
  "smoke:emergency:owner:$OWNER_ID" \
  "smoke:emergency:seed:$OWNER_ID"

CATEGORY_ID=$(docker compose exec -T db psql -U dofast -d dofast -tAc \
  "SELECT id FROM job_categories WHERE slug='mala-paczka' AND active=TRUE;")
CATEGORY_ID="${CATEGORY_ID//[[:space:]]/}"
test -n "$CATEGORY_ID"

ACTIVE_JOB=$(curl --fail --silent \
  -H "Authorization: Bearer $OWNER_TOKEN" \
  -H 'Content-Type: application/json' \
  -d "{\"title\":\"Emergency active job\",\"description\":\"Active work used to verify emergency safety containment.\",\"price\":25.00,\"categoryId\":$CATEGORY_ID,\"location\":{\"latitude\":51.1128,\"longitude\":17.0601,\"publicLabel\":\"Wrocław\",\"privateLabel\":\"Emergency smoke address\"}}" \
  "$api/jobs")
printf '%s' "$ACTIVE_JOB" > /tmp/emergency-active-job.json
ACTIVE_JOB_ID=$(json_value /tmp/emergency-active-job.json id)

OPEN_JOB=$(curl --fail --silent \
  -H "Authorization: Bearer $OWNER_TOKEN" \
  -H 'Content-Type: application/json' \
  -d "{\"title\":\"Emergency open listing\",\"description\":\"This listing must be cancelled by the account sanction.\",\"price\":10.00,\"categoryId\":$CATEGORY_ID,\"location\":{\"latitude\":51.1128,\"longitude\":17.0601,\"publicLabel\":\"Wrocław\",\"privateLabel\":\"Emergency smoke address 2\"}}" \
  "$api/jobs")
printf '%s' "$OPEN_JOB" > /tmp/emergency-open-job.json
OPEN_JOB_ID=$(json_value /tmp/emergency-open-job.json id)

curl --fail --silent --output /dev/null -X POST \
  -H "Authorization: Bearer $WORKER_TOKEN" \
  "$api/jobs/$ACTIVE_JOB_ID/accept"

CAPTURED_AT=$(python3 - <<'PY'
from datetime import datetime, timezone
print(datetime.now(timezone.utc).isoformat().replace('+00:00', 'Z'))
PY
)
TRACKING=$(curl --fail --silent -X PUT \
  -H "Authorization: Bearer $WORKER_TOKEN" \
  -H 'Content-Type: application/json' \
  -d "{\"latitude\":51.1200,\"longitude\":17.0700,\"accuracyMeters\":5.0,\"headingDegrees\":180.0,\"speedMetersPerSecond\":4.0,\"capturedAt\":\"$CAPTURED_AT\"}" \
  "$api/jobs/$ACTIVE_JOB_ID/tracking/location")
echo "$TRACKING" | grep -q '"sharingActive":true'

HELD_BEFORE=$(docker compose exec -T db psql -U dofast -d dofast -tAc \
  "SELECT status FROM escrow_transactions WHERE job_id=$ACTIVE_JOB_ID;")
test "${HELD_BEFORE//[[:space:]]/}" = "HELD"

REPORT=$(curl --fail --silent \
  -H "Authorization: Bearer $REPORTER_TOKEN" \
  -H 'Content-Type: application/json' \
  -d '{"reason":"FRAUD","details":"Emergency safety smoke report"}' \
  "$api/job-reports/jobs/$ACTIVE_JOB_ID")
printf '%s' "$REPORT" > /tmp/emergency-report.json
REPORT_ID=$(json_value /tmp/emergency-report.json id)

curl --fail --silent --output /tmp/emergency-reviewed.json -X PATCH \
  -H "Authorization: Bearer $ADMIN_TOKEN" \
  -H 'Content-Type: application/json' \
  -d '{"status":"REVIEWED","note":"Runtime-confirmed emergency safety risk"}' \
  "$api/admin/job-reports/$REPORT_ID"

SAFE_STATUS=$(curl --silent --output /tmp/emergency-safe-rejected.json --write-out '%{http_code}' \
  -X POST \
  -H "Authorization: Bearer $ADMIN_TOKEN" \
  -H 'Content-Type: application/json' \
  -d '{"action":"SUSPEND_JOB_OWNER","reason":"must reject while active"}' \
  "$api/admin/job-reports/$REPORT_ID/account-enforcement")
test "$SAFE_STATUS" = "409"

OWNER_STATUS_BEFORE=$(docker compose exec -T db psql -U dofast -d dofast -tAc \
  "SELECT status FROM users WHERE id=$OWNER_ID;")
test "${OWNER_STATUS_BEFORE//[[:space:]]/}" = "ACTIVE"

EMERGENCY=$(curl --fail --silent -X POST \
  -H "Authorization: Bearer $ADMIN_TOKEN" \
  -H 'Content-Type: application/json' \
  -d '{"action":"EMERGENCY_SUSPEND_JOB_OWNER","reason":"immediate runtime safety containment"}' \
  "$api/admin/job-reports/$REPORT_ID/account-enforcement")
printf '%s' "$EMERGENCY" > /tmp/emergency-enforcement.json
python3 - <<'PY'
import json
with open('/tmp/emergency-enforcement.json', encoding='utf-8') as fh:
    data=json.load(fh)
assert data['action'] == 'EMERGENCY_SUSPEND_JOB_OWNER', data
assert data['reason'] == 'immediate runtime safety containment', data
PY

ACCOUNT_STATE=$(docker compose exec -T db psql -U dofast -d dofast -tAc \
  "SELECT status || ':' || auth_version FROM users WHERE id=$OWNER_ID;")
test "${ACCOUNT_STATE//[[:space:]]/}" = "SUSPENDED:1"

ACTIVE_JOB_STATUS=$(docker compose exec -T db psql -U dofast -d dofast -tAc \
  "SELECT status FROM jobs WHERE id=$ACTIVE_JOB_ID;")
test "${ACTIVE_JOB_STATUS//[[:space:]]/}" = "DISPUTED"
OPEN_JOB_STATUS=$(docker compose exec -T db psql -U dofast -d dofast -tAc \
  "SELECT status FROM jobs WHERE id=$OPEN_JOB_ID;")
test "${OPEN_JOB_STATUS//[[:space:]]/}" = "CANCELLED"

ESCROW_STATE=$(docker compose exec -T db psql -U dofast -d dofast -tAc \
  "SELECT status || ':' || (resolved_at IS NULL)::int FROM escrow_transactions WHERE job_id=$ACTIVE_JOB_ID;")
test "${ESCROW_STATE//[[:space:]]/}" = "HELD:1"

DISPUTE_STATE=$(docker compose exec -T db psql -U dofast -d dofast -tAc \
  "SELECT reason || ':' || status || ':' || assigned_admin_id FROM disputes WHERE job_id=$ACTIVE_JOB_ID ORDER BY id DESC LIMIT 1;")
test "${DISPUTE_STATE//[[:space:]]/}" = "SAFETY_CONCERN:UNDER_REVIEW:$ADMIN_ID"

TRACKING_STATE=$(docker compose exec -T db psql -U dofast -d dofast -tAc \
  "SELECT (current_location IS NULL)::int || ':' || (sharing_stopped_at IS NOT NULL)::int FROM job_live_tracking WHERE job_id=$ACTIVE_JOB_ID;")
test "${TRACKING_STATE//[[:space:]]/}" = "1:1"

ACTIVE_REFRESH=$(docker compose exec -T db psql -U dofast -d dofast -tAc \
  "SELECT COUNT(*) FROM auth_refresh_sessions WHERE user_id=$OWNER_ID AND revoked_at IS NULL;")
test "${ACTIVE_REFRESH//[[:space:]]/}" = "0"

OLD_ACCESS_STATUS=$(curl --silent --output /tmp/emergency-old-owner-token.json --write-out '%{http_code}' \
  -H "Authorization: Bearer $OWNER_TOKEN" \
  "$api/users/me")
test "$OLD_ACCESS_STATUS" = "401"

WORKER_DISPUTES=$(curl --fail --silent \
  -H "Authorization: Bearer $WORKER_TOKEN" \
  "$api/disputes/my")
printf '%s' "$WORKER_DISPUTES" > /tmp/emergency-worker-disputes.json
python3 - "$ACTIVE_JOB_ID" <<'PY'
import json,sys
job_id=int(sys.argv[1])
with open('/tmp/emergency-worker-disputes.json', encoding='utf-8') as fh:
    data=json.load(fh)
matching=[d for d in data if d['jobId'] == job_id]
assert len(matching) == 1, data
assert matching[0]['reason'] == 'SAFETY_CONCERN', matching[0]
assert matching[0]['status'] == 'UNDER_REVIEW', matching[0]
PY

echo 'Emergency enforcement preserves escrow, opens an admin safety dispute, clears tracking, revokes sessions, cancels open listings, and suspends the account: OK'
