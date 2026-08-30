#!/usr/bin/env bash
set -euo pipefail
trap 'echo "Auth session smoke assertion failed at line $LINENO"' ERR

api='http://localhost:8080'
email='auth-session-smoke@example.com'
password='AuthSessionPass123!'
cookie_jar='/tmp/dofast-auth-session.cookies'
pre_logout_jar='/tmp/dofast-auth-session-pre-logout.cookies'
login_headers='/tmp/dofast-auth-session-login.headers'
refresh_headers='/tmp/dofast-auth-session-refresh.headers'
logout_headers='/tmp/dofast-auth-session-logout.headers'

rm -f "$cookie_jar" "$pre_logout_jar" "$login_headers" "$refresh_headers" "$logout_headers" \
  /tmp/dofast-auth-session-*.json

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

curl --fail --silent -H 'Content-Type: application/json' \
  -d "{\"email\":\"$email\",\"nickname\":\"authsession\",\"password\":\"$password\"}" \
  "$api/users" > /tmp/dofast-auth-session-register.json

curl --fail --silent -D "$login_headers" -c "$cookie_jar" \
  -H 'Content-Type: application/json' \
  -d "{\"email\":\"$email\",\"password\":\"$password\"}" \
  "$api/users/login" > /tmp/dofast-auth-session-login.json

LOGIN_ACCESS=$(json_value /tmp/dofast-auth-session-login.json accessToken)
test -n "$LOGIN_ACCESS"

REFRESH_SET_COOKIE=$(grep -i '^set-cookie: dofast_refresh=' "$login_headers" | tr -d '\r')
CSRF_SET_COOKIE=$(grep -i '^set-cookie: dofast_csrf=' "$login_headers" | tr -d '\r')
test -n "$REFRESH_SET_COOKIE"
test -n "$CSRF_SET_COOKIE"
echo "$REFRESH_SET_COOKIE" | grep -qi 'HttpOnly'
echo "$REFRESH_SET_COOKIE" | grep -qi 'SameSite=Strict'
if echo "$CSRF_SET_COOKIE" | grep -qi 'HttpOnly'; then
  echo 'CSRF cookie must stay readable by the browser client'
  exit 1
fi
echo "$CSRF_SET_COOKIE" | grep -qi 'SameSite=Strict'

CSRF_TOKEN=$(cookie_value dofast_csrf)
REFRESH_TOKEN=$(cookie_value dofast_refresh)
test -n "$CSRF_TOKEN"
test -n "$REFRESH_TOKEN"

curl --fail --silent -H "Authorization: Bearer $LOGIN_ACCESS" \
  "$api/users/me" > /tmp/dofast-auth-session-me.json
test "$(json_value /tmp/dofast-auth-session-me.json email)" = "$email"

echo 'Login issues HttpOnly refresh + readable CSRF cookies and short bearer works: OK'

BAD_CSRF_STATUS=$(curl --silent --output /tmp/dofast-auth-session-bad-csrf.json --write-out '%{http_code}' \
  -b "$cookie_jar" -c "$cookie_jar" \
  -H 'X-CSRF-Token: definitely-wrong-csrf' \
  -X POST "$api/users/session/refresh")
test "$BAD_CSRF_STATUS" = "403"

curl --fail --silent -D "$refresh_headers" -b "$cookie_jar" -c "$cookie_jar" \
  -H "X-CSRF-Token: $CSRF_TOKEN" \
  -X POST "$api/users/session/refresh" > /tmp/dofast-auth-session-refresh.json

REFRESHED_ACCESS=$(json_value /tmp/dofast-auth-session-refresh.json accessToken)
test -n "$REFRESHED_ACCESS"
NEW_CSRF_TOKEN=$(cookie_value dofast_csrf)
NEW_REFRESH_TOKEN=$(cookie_value dofast_refresh)
test -n "$NEW_CSRF_TOKEN"
test -n "$NEW_REFRESH_TOKEN"
test "$NEW_CSRF_TOKEN" != "$CSRF_TOKEN"
test "$NEW_REFRESH_TOKEN" != "$REFRESH_TOKEN"

grep -i '^set-cookie: dofast_refresh=' "$refresh_headers" | grep -qi 'HttpOnly'
grep -i '^set-cookie: dofast_csrf=' "$refresh_headers" | grep -qi 'SameSite=Strict'

curl --fail --silent -H "Authorization: Bearer $REFRESHED_ACCESS" \
  "$api/users/me" > /tmp/dofast-auth-session-refreshed-me.json

test "$(json_value /tmp/dofast-auth-session-refreshed-me.json email)" = "$email"

SESSION_COUNTS=$(docker compose exec -T db psql -U dofast -d dofast -tAc \
  "SELECT COUNT(*) || ':' || COUNT(*) FILTER (WHERE s.revoked_at IS NULL) || ':' || COUNT(*) FILTER (WHERE s.revocation_reason='ROTATED') FROM auth_refresh_sessions s JOIN users u ON u.id=s.user_id WHERE u.email='$email';")
SESSION_COUNTS="${SESSION_COUNTS//[[:space:]]/}"
test "$SESSION_COUNTS" = "2:1:1"

HASH_SHAPE=$(docker compose exec -T db psql -U dofast -d dofast -tAc \
  "SELECT MIN(length(s.token_hash)) || ':' || MAX(length(s.token_hash)) || ':' || MIN(length(s.csrf_hash)) || ':' || MAX(length(s.csrf_hash)) FROM auth_refresh_sessions s JOIN users u ON u.id=s.user_id WHERE u.email='$email';")
HASH_SHAPE="${HASH_SHAPE//[[:space:]]/}"
test "$HASH_SHAPE" = "64:64:64:64"

RAW_SECRET_MATCHES=$(docker compose exec -T db psql -U dofast -d dofast -tAc \
  "SELECT COUNT(*) FROM auth_refresh_sessions s JOIN users u ON u.id=s.user_id WHERE u.email='$email' AND (s.token_hash IN ('$REFRESH_TOKEN','$NEW_REFRESH_TOKEN') OR s.csrf_hash IN ('$CSRF_TOKEN','$NEW_CSRF_TOKEN'));" )
test "${RAW_SECRET_MATCHES//[[:space:]]/}" = "0"

echo 'Refresh rotates the family, preserves one active session, and stores hashes only: OK'

cp "$cookie_jar" "$pre_logout_jar"
PRE_LOGOUT_CSRF="$NEW_CSRF_TOKEN"

curl --fail --silent -D "$logout_headers" -b "$cookie_jar" -c "$cookie_jar" \
  -H "X-CSRF-Token: $PRE_LOGOUT_CSRF" \
  -X POST "$api/users/session/logout" > /dev/null

grep -i '^set-cookie: dofast_refresh=' "$logout_headers" | grep -qi 'Max-Age=0'
grep -i '^set-cookie: dofast_csrf=' "$logout_headers" | grep -qi 'Max-Age=0'
if awk '$6 == "dofast_refresh" || $6 == "dofast_csrf" { found=1 } END { exit found ? 0 : 1 }' "$cookie_jar"; then
  echo 'Logout cookie jar still contains auth session cookies'
  exit 1
fi

POST_LOGOUT_STATUS=$(curl --silent --output /tmp/dofast-auth-session-after-logout.json --write-out '%{http_code}' \
  -b "$pre_logout_jar" \
  -H "X-CSRF-Token: $PRE_LOGOUT_CSRF" \
  -X POST "$api/users/session/refresh")
test "$POST_LOGOUT_STATUS" != "200"

ACTIVE_AFTER_LOGOUT=$(docker compose exec -T db psql -U dofast -d dofast -tAc \
  "SELECT COUNT(*) FROM auth_refresh_sessions s JOIN users u ON u.id=s.user_id WHERE u.email='$email' AND s.revoked_at IS NULL;")
test "${ACTIVE_AFTER_LOGOUT//[[:space:]]/}" = "0"

echo 'Logout clears browser cookies and revokes refresh authority: OK'
