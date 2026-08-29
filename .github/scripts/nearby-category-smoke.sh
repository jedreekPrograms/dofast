#!/usr/bin/env bash
set -euo pipefail
trap 'echo "Nearby category smoke failed at line $LINENO"' ERR

api='http://localhost:8080'

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

curl --fail --silent --show-error \
  -H 'Content-Type: application/json' \
  -d '{"email":"nearby-category@example.com","nickname":"nearbyCategory","password":"NearbyPass123!"}' \
  "$api/users" > /tmp/nearby-category-register.json

curl --fail --silent --show-error \
  -H 'Content-Type: application/json' \
  -d '{"email":"nearby-category@example.com","password":"NearbyPass123!"}' \
  "$api/users/login" > /tmp/nearby-category-login.json

OWNER_ID=$(json_value /tmp/nearby-category-register.json id)
TOKEN=$(json_value /tmp/nearby-category-login.json accessToken)
MONTAGE_CATEGORY=$(docker compose exec -T db psql -U dofast -d dofast -tAc "SELECT id FROM job_categories WHERE slug='montaz-mebli' AND active=TRUE;" | tr -d '[:space:]')
CLEANING_CATEGORY=$(docker compose exec -T db psql -U dofast -d dofast -tAc "SELECT id FROM job_categories WHERE slug='sprzatanie-mieszkania' AND active=TRUE;" | tr -d '[:space:]')
test -n "$MONTAGE_CATEGORY"
test -n "$CLEANING_CATEGORY"

# Nearby discovery needs spendable fixture value only; keep it non-withdrawable.
bash .github/scripts/seed-wallet-funding.sh \
  "$OWNER_ID" 200.00 PLATFORM_ADJUSTMENT \
  "smoke:nearby-category:funding:$OWNER_ID" \
  "smoke:nearby-category:seed:$OWNER_ID"

create_job() {
  local category_id="$1"
  local title="$2"
  local latitude="$3"
  local longitude="$4"
  local output="$5"

  curl --fail --silent --show-error \
    -H "Authorization: Bearer $TOKEN" \
    -H 'Content-Type: application/json' \
    -d "{\"title\":\"$title\",\"description\":\"Public nearby category PostGIS smoke job.\",\"price\":30.00,\"categoryId\":$category_id,\"location\":{\"latitude\":$latitude,\"longitude\":$longitude,\"publicLabel\":\"Wrocław, Test\",\"privateLabel\":\"Smoke exact address\",\"placeId\":null}}" \
    "$api/jobs" > "$output"
}

create_job "$MONTAGE_CATEGORY" 'Nearby montage smoke' 51.1002 17.0302 /tmp/nearby-montage.json
create_job "$CLEANING_CATEGORY" 'Nearby cleaning smoke' 51.1004 17.0304 /tmp/nearby-cleaning.json

MONTAGE_ID=$(json_value /tmp/nearby-montage.json id)
CLEANING_ID=$(json_value /tmp/nearby-cleaning.json id)

ALL=$(curl --fail --silent --show-error "$api/jobs/nearby?latitude=51.1&longitude=17.03&radiusMeters=5000&limit=20")
LEAF=$(curl --fail --silent --show-error "$api/jobs/nearby?latitude=51.1&longitude=17.03&radiusMeters=5000&category=montaz-mebli&limit=20")
PARENT=$(curl --fail --silent --show-error "$api/jobs/nearby?latitude=51.1&longitude=17.03&radiusMeters=5000&category=dom-remont&limit=20")
CLEANING_PARENT=$(curl --fail --silent --show-error "$api/jobs/nearby?latitude=51.1&longitude=17.03&radiusMeters=5000&category=sprzatanie&limit=20")

python3 - "$MONTAGE_ID" "$CLEANING_ID" "$ALL" "$LEAF" "$PARENT" "$CLEANING_PARENT" <<'PY'
import json,sys
montage=int(sys.argv[1]); cleaning=int(sys.argv[2])
all_jobs=json.loads(sys.argv[3]); leaf=json.loads(sys.argv[4]); parent=json.loads(sys.argv[5]); cleaning_parent=json.loads(sys.argv[6])
ids=lambda rows: {int(row['id']) for row in rows}
assert {montage, cleaning}.issubset(ids(all_jobs))
assert ids(leaf) == {montage}
assert ids(parent) == {montage}
assert ids(cleaning_parent) == {cleaning}
for rows in (leaf, parent, cleaning_parent):
    assert all('Smoke exact address' not in json.dumps(row) for row in rows)
PY

echo 'Nearby PostGIS discovery supports leaf and parent category slugs without leaking exact addresses: OK'
