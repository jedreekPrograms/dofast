#!/usr/bin/env bash
set -euo pipefail
trap 'echo "Password recovery smoke assertion failed at line $LINENO"' ERR

api='http://localhost:8080'
email='password-recovery-smoke@example.com'
old_password='RecoveryOldPass123!'
new_password='RecoveryNewPass456!'
raw_reset_token='runtime-known-password-reset-token-2026'
cookie_jar='/tmp/dofast-password-recovery.cookies'

rm -f "$cookie_jar" /tmp/dofast-password-recovery-*.json

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

cookie_value() {
  local name="$1"
  awk -v name="$name" '$6 == name { value=$7 } END { print value }' "$cookie_jar"
}

RESET_HASH=$(python3 - "$raw_reset_token" <<'PY'
import hashlib,sys
print(hashlib.sha256(sys.argv[1].encode()).hexdigest())
PY
)

test "${#RESET_HASH}" = "64"

curl --fail --silent -H 'Content-Type: application/json' \
  -d "{\"email\":\"$email\",\"nickname\":\"recoverysmoke\",\"password\":\"$old_password\"}" \
  "$api/users" > /tmp/dofast-password-recovery-register.json

curl --fail --silent -c "$cookie_jar" -H 'Content-Type: application/json' \
  -d "{\"email\":\"$email\",\"password\":\"$old_password\"}" \
  "$api/users/login" > /tmp/dofast-password-recovery-login.json

USER_ID=$(json_value /tmp/dofast-password-recovery-register.json id)
OLD_ACCESS=$(json_value /tmp/dofast-password-recovery-login.json accessToken)
OLD_CSRF=$(cookie_value dofast_csrf)
test -n "$USER_ID"
test -n "$OLD_ACCESS"
test -n "$OLD_CSRF"

python3 .github/scripts/websocket-auth-version-smoke.py "$OLD_ACCESS" connected

EXISTING_FORGOT_STATUS=$(curl --silent --output /tmp/dofast-password-recovery-forgot-existing.json --write-out '%{http_code}' \
  -H 'Content-Type: application/json' \
  -d "{\"email\":\"$email\"}" \
  "$api/users/password/forgot")
MISSING_FORGOT_STATUS=$(curl --silent --output /tmp/dofast-password-recovery-forgot-missing.json --write-out '%{http_code}' \
  -H 'Content-Type: application/json' \
  -d '{"email":"definitely-missing-password-recovery@example.com"}' \
  "$api/users/password/forgot")
test "$EXISTING_FORGOT_STATUS" = "202"
test "$MISSING_FORGOT_STATUS" = "202"
test ! -s /tmp/dofast-password-recovery-forgot-existing.json
test ! -s /tmp/dofast-password-recovery-forgot-missing.json

echo 'Forgot-password response does not disclose account existence: OK'

# Local/CI delivery is intentionally disabled. Seed a known hash directly so this smoke can exercise
# the public reset boundary without ever adding a development endpoint that reveals raw reset tokens.
docker compose exec -T db psql -U dofast -d dofast -v ON_ERROR_STOP=1 -c \
  "INSERT INTO auth_password_reset_tokens(user_id, token_hash, created_at, expires_at) VALUES ($USER_ID, '$RESET_HASH', NOW(), NOW() + INTERVAL '20 minutes');" >/dev/null

RAW_TOKEN_ROWS=$(docker compose exec -T db psql -U dofast -d dofast -tAc \
  "SELECT COUNT(*) FROM auth_password_reset_tokens WHERE token_hash='$raw_reset_token';")
test "${RAW_TOKEN_ROWS//[[:space:]]/}" = "0"

RESET_STATUS=$(curl --silent --output /tmp/dofast-password-recovery-reset.json --write-out '%{http_code}' \
  -H 'Content-Type: application/json' \
  -d "{\"token\":\"$raw_reset_token\",\"newPassword\":\"$new_password\"}" \
  "$api/users/password/reset")
test "$RESET_STATUS" = "204"

TOKEN_STATE=$(docker compose exec -T db psql -U dofast -d dofast -tAc \
  "SELECT (used_at IS NOT NULL)::int || ':' || (invalidated_at IS NULL)::int FROM auth_password_reset_tokens WHERE token_hash='$RESET_HASH';")
test "${TOKEN_STATE//[[:space:]]/}" = "1:1"

AUTH_VERSION=$(docker compose exec -T db psql -U dofast -d dofast -tAc \
  "SELECT auth_version FROM users WHERE id=$USER_ID;")
test "${AUTH_VERSION//[[:space:]]/}" = "1"

OLD_ACCESS_STATUS=$(curl --silent --output /tmp/dofast-password-recovery-old-access.json --write-out '%{http_code}' \
  -H "Authorization: Bearer $OLD_ACCESS" \
  "$api/users/me")
test "$OLD_ACCESS_STATUS" = "401"

python3 .github/scripts/websocket-auth-version-smoke.py "$OLD_ACCESS" rejected

OLD_REFRESH_STATUS=$(curl --silent --output /tmp/dofast-password-recovery-old-refresh.json --write-out '%{http_code}' \
  -b "$cookie_jar" \
  -H "X-CSRF-Token: $OLD_CSRF" \
  -X POST "$api/users/session/refresh")
test "$OLD_REFRESH_STATUS" != "200"

ACTIVE_REFRESH=$(docker compose exec -T db psql -U dofast -d dofast -tAc \
  "SELECT COUNT(*) FROM auth_refresh_sessions WHERE user_id=$USER_ID AND revoked_at IS NULL;")
test "${ACTIVE_REFRESH//[[:space:]]/}" = "0"

echo 'Password reset immediately invalidates access and refresh sessions: OK'

OLD_LOGIN_STATUS=$(curl --silent --output /tmp/dofast-password-recovery-old-login.json --write-out '%{http_code}' \
  -H 'Content-Type: application/json' \
  -d "{\"email\":\"$email\",\"password\":\"$old_password\"}" \
  "$api/users/login")
test "$OLD_LOGIN_STATUS" = "401"

NEW_LOGIN_STATUS=$(curl --silent --output /tmp/dofast-password-recovery-new-login.json --write-out '%{http_code}' \
  -H 'Content-Type: application/json' \
  -d "{\"email\":\"$email\",\"password\":\"$new_password\"}" \
  "$api/users/login")
test "$NEW_LOGIN_STATUS" = "200"

grep -q '"accessToken"' /tmp/dofast-password-recovery-new-login.json
NEW_ACCESS=$(json_value /tmp/dofast-password-recovery-new-login.json accessToken)
python3 .github/scripts/websocket-auth-version-smoke.py "$NEW_ACCESS" connected

REPLAY_STATUS=$(curl --silent --output /tmp/dofast-password-recovery-replay.json --write-out '%{http_code}' \
  -H 'Content-Type: application/json' \
  -d "{\"token\":\"$raw_reset_token\",\"newPassword\":\"AnotherPass789!\"}" \
  "$api/users/password/reset")
test "$REPLAY_STATUS" != "204"

echo 'Reset token is one-time and only the new password authenticates: OK'
