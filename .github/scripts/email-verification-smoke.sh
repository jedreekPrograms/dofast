#!/usr/bin/env bash
set -euo pipefail
trap 'echo "Email verification smoke assertion failed at line $LINENO"' ERR

api='http://localhost:8080'
email='email-verification-smoke@example.com'
password='VerifyEmailPass123!'
raw_token='runtime-known-email-verification-token-2026'
cookie_jar='/tmp/dofast-email-verification.cookies'

rm -f "$cookie_jar" /tmp/dofast-email-verification-*.json

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

TOKEN_HASH=$(python3 - "$raw_token" <<'PY'
import hashlib,sys
print(hashlib.sha256(sys.argv[1].encode()).hexdigest())
PY
)
test "${#TOKEN_HASH}" = "64"

REGISTER_STATUS=$(curl --silent --output /tmp/dofast-email-verification-register.json --write-out '%{http_code}' \
  -H 'Content-Type: application/json' \
  -d "{\"email\":\"$email\",\"nickname\":\"emailsmoke\",\"password\":\"$password\"}" \
  "$api/users")
test "$REGISTER_STATUS" = "201"
USER_ID=$(json_value /tmp/dofast-email-verification-register.json id)
test -n "$USER_ID"
test "$(json_value /tmp/dofast-email-verification-register.json emailVerified)" = "False"

UNVERIFIED_LOGIN_STATUS=$(curl --silent --output /tmp/dofast-email-verification-login-before.json --write-out '%{http_code}' \
  -H 'Content-Type: application/json' \
  -d "{\"email\":\"$email\",\"password\":\"$password\"}" \
  "$api/users/login")
test "$UNVERIFIED_LOGIN_STATUS" = "403"
test ! -f "$cookie_jar" || ! grep -q 'dofast_refresh' "$cookie_jar"

echo 'Unverified local account cannot receive a session: OK'

EXISTING_RESEND_STATUS=$(curl --silent --output /tmp/dofast-email-verification-resend-existing.json --write-out '%{http_code}' \
  -H 'Content-Type: application/json' \
  -d "{\"email\":\"$email\"}" \
  "$api/users/email-verification/resend")
MISSING_RESEND_STATUS=$(curl --silent --output /tmp/dofast-email-verification-resend-missing.json --write-out '%{http_code}' \
  -H 'Content-Type: application/json' \
  -d '{"email":"definitely-missing-email-verification@example.com"}' \
  "$api/users/email-verification/resend")
test "$EXISTING_RESEND_STATUS" = "202"
test "$MISSING_RESEND_STATUS" = "202"
test ! -s /tmp/dofast-email-verification-resend-existing.json
test ! -s /tmp/dofast-email-verification-resend-missing.json

ACTIVE_BEFORE=$(docker compose exec -T db psql -U dofast -d dofast -tAc \
  "SELECT COUNT(*) FROM email_verification_tokens WHERE user_id=$USER_ID AND used_at IS NULL AND invalidated_at IS NULL AND expires_at>NOW();")
TOTAL_BEFORE=$(docker compose exec -T db psql -U dofast -d dofast -tAc \
  "SELECT COUNT(*) FROM email_verification_tokens WHERE user_id=$USER_ID;")
BAD_HASH_ROWS=$(docker compose exec -T db psql -U dofast -d dofast -tAc \
  "SELECT COUNT(*) FROM email_verification_tokens WHERE user_id=$USER_ID AND length(token_hash)<>64;")
test "${ACTIVE_BEFORE//[[:space:]]/}" = "1"
test "${TOTAL_BEFORE//[[:space:]]/}" = "2"
test "${BAD_HASH_ROWS//[[:space:]]/}" = "0"

echo 'Resend is enumeration-safe and leaves exactly one active hash-only credential: OK'

# Outbound SMTP is intentionally unavailable in this runtime smoke. The real service still creates
# only a SHA-256 credential and attempts delivery AFTER_COMMIT. Replace the active hash with the SHA
# of a known CI-only token so the public verify boundary can be exercised without introducing any
# development endpoint that exposes raw verification credentials.
docker compose exec -T db psql -U dofast -d dofast -v ON_ERROR_STOP=1 -c \
  "UPDATE email_verification_tokens SET token_hash='$TOKEN_HASH' WHERE id=(SELECT id FROM email_verification_tokens WHERE user_id=$USER_ID AND used_at IS NULL AND invalidated_at IS NULL ORDER BY id DESC LIMIT 1);" >/dev/null

RAW_TOKEN_ROWS=$(docker compose exec -T db psql -U dofast -d dofast -tAc \
  "SELECT COUNT(*) FROM email_verification_tokens WHERE token_hash='$raw_token';")
test "${RAW_TOKEN_ROWS//[[:space:]]/}" = "0"

VERIFY_STATUS=$(curl --silent --output /tmp/dofast-email-verification-verify.json --write-out '%{http_code}' \
  -H 'Content-Type: application/json' \
  -d "{\"token\":\"$raw_token\"}" \
  "$api/users/email-verification/verify")
test "$VERIFY_STATUS" = "204"

VERIFIED_AT=$(docker compose exec -T db psql -U dofast -d dofast -tAc \
  "SELECT (email_verified_at IS NOT NULL)::int FROM users WHERE id=$USER_ID;")
TOKEN_STATE=$(docker compose exec -T db psql -U dofast -d dofast -tAc \
  "SELECT (used_at IS NOT NULL)::int || ':' || (invalidated_at IS NULL)::int FROM email_verification_tokens WHERE token_hash='$TOKEN_HASH';")
test "${VERIFIED_AT//[[:space:]]/}" = "1"
test "${TOKEN_STATE//[[:space:]]/}" = "1:1"

REPLAY_STATUS=$(curl --silent --output /tmp/dofast-email-verification-replay.json --write-out '%{http_code}' \
  -H 'Content-Type: application/json' \
  -d "{\"token\":\"$raw_token\"}" \
  "$api/users/email-verification/verify")
test "$REPLAY_STATUS" != "204"

echo 'Verification credential is single-use and marks the account verified: OK'

LOGIN_STATUS=$(curl --silent --output /tmp/dofast-email-verification-login-after.json --write-out '%{http_code}' \
  -c "$cookie_jar" \
  -H 'Content-Type: application/json' \
  -d "{\"email\":\"$email\",\"password\":\"$password\"}" \
  "$api/users/login")
test "$LOGIN_STATUS" = "200"
grep -q '"accessToken"' /tmp/dofast-email-verification-login-after.json
grep -q 'dofast_refresh' "$cookie_jar"

AFTER_RESEND_STATUS=$(curl --silent --output /tmp/dofast-email-verification-resend-after.json --write-out '%{http_code}' \
  -H 'Content-Type: application/json' \
  -d "{\"email\":\"$email\"}" \
  "$api/users/email-verification/resend")
test "$AFTER_RESEND_STATUS" = "202"
ACTIVE_AFTER=$(docker compose exec -T db psql -U dofast -d dofast -tAc \
  "SELECT COUNT(*) FROM email_verification_tokens WHERE user_id=$USER_ID AND used_at IS NULL AND invalidated_at IS NULL AND expires_at>NOW();")
test "${ACTIVE_AFTER//[[:space:]]/}" = "0"

echo 'Verified account authenticates and resend does not create another credential: OK'
