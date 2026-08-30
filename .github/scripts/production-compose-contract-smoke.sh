#!/usr/bin/env bash
set -euo pipefail
trap 'echo "Production Compose contract smoke failed at line $LINENO"' ERR

compose_file='infra/compose/compose.prod.yaml'
prod_profile='apps/api/src/main/resources/application-prod.yml'
rendered=$(mktemp)
trap 'rm -f "$rendered"' EXIT

export DB_NAME=dofast_prod_contract
export DB_USER=dofast_prod_contract
export DB_PASSWORD='prod-contract-db-password'
export DB_POOL_MAX_SIZE=17
export DB_POOL_MIN_IDLE=3
export JWT_SECRET='prod-contract-jwt-secret-which-is-long-enough-for-validation'
export JWT_EXPIRATION_MS=600000
export AUTH_REFRESH_TTL_DAYS=21
export AUTH_REFRESH_REUSE_GRACE_SECONDS=20
export AUTH_SESSION_RETENTION_DAYS=9
export AUTH_SESSION_CLEANUP_INTERVAL_MS=7200000
export AUTH_COOKIE_SAME_SITE=Strict
export SMTP_HOST='smtp.example.test'
export SMTP_PORT=2525
export SMTP_USERNAME='dofast-prod-contract'
export SMTP_PASSWORD='prod-contract-smtp-password'
export PASSWORD_RESET_BASE_URL='https://app.example.test/reset-password'
export PASSWORD_RECOVERY_FROM_ADDRESS='security@example.test'
export PASSWORD_RESET_TTL_MINUTES=25
export PASSWORD_RESET_RETENTION_DAYS=8
export PASSWORD_RESET_CLEANUP_INTERVAL_MS=5400000
export WEBSOCKET_ALLOWED_ORIGIN_PATTERNS='https://app.example.test'
export STRIPE_SECRET_KEY='sk_test_prod_contract'
export STRIPE_WEBHOOK_SECRET='whsec_prod_contract'
export PLATFORM_FEE_BASIS_POINTS=175
export PAYOUT_PROVIDER=disabled
export PAYOUT_SANDBOX_ENABLED=false
export PAYOUT_MINIMUM_AMOUNT=5.00
export PAYOUT_MAX_ATTEMPTS=7
export PAYOUT_RETRY_BASE_SECONDS=30
export PAYOUT_STALE_PROCESSING_SECONDS=600
export PAYOUT_SUBMITTED_RECONCILIATION_SECONDS=900
export PAYOUT_DISPATCH_INTERVAL_MS=4000
export PAYOUT_STRIPE_CONNECT_ENABLED=false
export PAYOUT_STRIPE_CONNECT_DISPATCH_ENABLED=false
export PAYOUT_STRIPE_CONNECT_RECONCILIATION_ENABLED=true
export PAYOUT_STRIPE_CONNECT_RECONCILIATION_INTERVAL_MS=45000
export PAYOUT_STRIPE_CONNECT_COUNTRY=PL
export PAYOUT_STRIPE_CONNECT_REFRESH_URL='https://app.example.test/wallet?stripe-connect=refresh'
export PAYOUT_STRIPE_CONNECT_RETURN_URL='https://app.example.test/wallet?stripe-connect=return'
export ROUTING_PROVIDER=google
export GOOGLE_MAPS_ROUTES_API_KEY='prod-contract-routes-key'
export TRACKING_CHECKPOINT_ARRIVAL_RADIUS_METERS=75
export ATTACHMENT_ENCRYPTION_KEY_BASE64="$(python3 - <<'PY'
import base64
print(base64.b64encode(b'P' * 32).decode())
PY
)"
export ATTACHMENT_MAX_FILE_SIZE=8MB
export ATTACHMENT_MAX_REQUEST_SIZE=9MB
export ATTACHMENT_MAX_FILE_SIZE_BYTES=8388608
export ATTACHMENT_MAX_PER_JOB=9
export VITE_STRIPE_PUBLISHABLE_KEY='pk_test_prod_contract'
export VITE_GOOGLE_MAPS_BROWSER_KEY='prod-contract-browser-key'
export VITE_GOOGLE_MAPS_MAP_ID='prod-contract-map-id'

docker compose -f "$compose_file" config --format json > "$rendered"

python3 - "$rendered" <<'PY'
import json
import sys

with open(sys.argv[1], encoding='utf-8') as handle:
    config = json.load(handle)

services = config['services']
api = services['api']
web = services['web']
env = api['environment']

expected = {
    'DB_POOL_MAX_SIZE': '17',
    'DB_POOL_MIN_IDLE': '3',
    'JWT_EXPIRATION_MS': '600000',
    'AUTH_REFRESH_TTL_DAYS': '21',
    'AUTH_REFRESH_REUSE_GRACE_SECONDS': '20',
    'AUTH_SESSION_RETENTION_DAYS': '9',
    'AUTH_SESSION_CLEANUP_INTERVAL_MS': '7200000',
    'AUTH_COOKIE_SAME_SITE': 'Strict',
    'SMTP_HOST': 'smtp.example.test',
    'SMTP_PORT': '2525',
    'SMTP_USERNAME': 'dofast-prod-contract',
    'SMTP_PASSWORD': 'prod-contract-smtp-password',
    'PASSWORD_RESET_BASE_URL': 'https://app.example.test/reset-password',
    'PASSWORD_RECOVERY_FROM_ADDRESS': 'security@example.test',
    'PASSWORD_RESET_TTL_MINUTES': '25',
    'PASSWORD_RESET_RETENTION_DAYS': '8',
    'PASSWORD_RESET_CLEANUP_INTERVAL_MS': '5400000',
    'PLATFORM_FEE_BASIS_POINTS': '175',
    'PAYOUT_PROVIDER': 'disabled',
    'PAYOUT_SANDBOX_ENABLED': 'false',
    'PAYOUT_MINIMUM_AMOUNT': '5.00',
    'PAYOUT_MAX_ATTEMPTS': '7',
    'PAYOUT_RETRY_BASE_SECONDS': '30',
    'PAYOUT_STALE_PROCESSING_SECONDS': '600',
    'PAYOUT_SUBMITTED_RECONCILIATION_SECONDS': '900',
    'PAYOUT_DISPATCH_INTERVAL_MS': '4000',
    'PAYOUT_STRIPE_CONNECT_ENABLED': 'false',
    'PAYOUT_STRIPE_CONNECT_DISPATCH_ENABLED': 'false',
    'PAYOUT_STRIPE_CONNECT_RECONCILIATION_ENABLED': 'true',
    'PAYOUT_STRIPE_CONNECT_RECONCILIATION_INTERVAL_MS': '45000',
    'PAYOUT_STRIPE_CONNECT_COUNTRY': 'PL',
    'PAYOUT_STRIPE_CONNECT_REFRESH_URL': 'https://app.example.test/wallet?stripe-connect=refresh',
    'PAYOUT_STRIPE_CONNECT_RETURN_URL': 'https://app.example.test/wallet?stripe-connect=return',
    'TRACKING_CHECKPOINT_ARRIVAL_RADIUS_METERS': '75',
    'ATTACHMENT_STORAGE_ROOT': '/var/lib/dofast/attachments',
    'ATTACHMENT_MAX_FILE_SIZE': '8MB',
    'ATTACHMENT_MAX_REQUEST_SIZE': '9MB',
    'ATTACHMENT_MAX_FILE_SIZE_BYTES': '8388608',
    'ATTACHMENT_MAX_PER_JOB': '9',
}
for key, value in expected.items():
    assert env.get(key) == value, (key, env.get(key), value)

assert 'AUTH_COOKIE_SECURE' not in env, 'production Compose must not override the prod Secure-cookie invariant'
assert env['ATTACHMENT_ENCRYPTION_KEY_BASE64'], 'production attachment encryption key was dropped'
assert env['PAYOUT_SANDBOX_ENABLED'] == 'false', 'production contract must not enable sandbox payout mode'

attachment_mounts = [
    volume for volume in api.get('volumes', [])
    if volume.get('target') == '/var/lib/dofast/attachments'
]
assert len(attachment_mounts) == 1, attachment_mounts
assert attachment_mounts[0].get('type') == 'volume', attachment_mounts[0]
assert attachment_mounts[0].get('source'), attachment_mounts[0]

assert api.get('healthcheck', {}).get('test'), 'API healthcheck missing'
assert web.get('healthcheck', {}).get('test'), 'web healthcheck missing'
assert web.get('depends_on', {}).get('api', {}).get('condition') == 'service_healthy', web.get('depends_on')
PY

# The prod Spring profile owns the Secure-cookie and password-recovery delivery invariants.
python3 - "$prod_profile" <<'PY'
import pathlib
import sys

text = pathlib.Path(sys.argv[1]).read_text(encoding='utf-8')
assert 'expiration-ms: ${JWT_EXPIRATION_MS:600000}' in text, 'production access-token default is not 10 minutes'
assert 'cookie-secure: true' in text, 'production refresh cookies are not hard-wired Secure'
assert 'same-site: ${AUTH_COOKIE_SAME_SITE:Strict}' in text, 'production SameSite policy missing'
assert 'delivery: smtp' in text, 'production password recovery must use SMTP delivery'
assert 'reset-base-url: ${PASSWORD_RESET_BASE_URL}' in text, 'production password reset URL is not required'
assert 'from-address: ${PASSWORD_RECOVERY_FROM_ADDRESS}' in text, 'production password recovery sender is not required'
PY

# The production deployment must fail closed instead of silently inheriting local/CI secrets or
# silently disabling account-recovery delivery when an operator forgets required configuration.
for missing in ATTACHMENT_ENCRYPTION_KEY_BASE64 SMTP_HOST PASSWORD_RESET_BASE_URL PASSWORD_RECOVERY_FROM_ADDRESS; do
  if env -u "$missing" docker compose -f "$compose_file" config >/tmp/dofast-prod-compose-missing-secret.log 2>&1; then
    echo "Production Compose unexpectedly accepted missing $missing"
    cat /tmp/dofast-prod-compose-missing-secret.log
    exit 1
  fi
done
rm -f /tmp/dofast-prod-compose-missing-secret.log

echo 'Production Compose forwards finance/payout/auth/password-recovery/tracking settings, enforces Secure refresh cookies, and persists encrypted attachments: OK'
