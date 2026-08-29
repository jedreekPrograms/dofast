#!/usr/bin/env bash
set -euo pipefail
trap 'echo "Production Compose contract smoke failed at line $LINENO"' ERR

compose_file='infra/compose/compose.prod.yaml'
rendered=$(mktemp)
trap 'rm -f "$rendered"' EXIT

export DB_NAME=dofast_prod_contract
export DB_USER=dofast_prod_contract
export DB_PASSWORD='prod-contract-db-password'
export DB_POOL_MAX_SIZE=17
export DB_POOL_MIN_IDLE=3
export JWT_SECRET='prod-contract-jwt-secret-which-is-long-enough-for-validation'
export JWT_EXPIRATION_MS=1800000
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

# The production deployment must fail closed instead of silently inheriting the local/CI
# attachment encryption key when the operator forgets the secret.
if env -u ATTACHMENT_ENCRYPTION_KEY_BASE64 \
  docker compose -f "$compose_file" config >/tmp/dofast-prod-compose-missing-secret.log 2>&1; then
  echo 'Production Compose unexpectedly accepted a missing attachment encryption key'
  cat /tmp/dofast-prod-compose-missing-secret.log
  exit 1
fi
rm -f /tmp/dofast-prod-compose-missing-secret.log

echo 'Production Compose forwards finance/payout/reconciliation/tracking settings and persists encrypted attachments: OK'
