#!/usr/bin/env bash
set -euo pipefail
trap 'echo "Job attachments smoke failed at line $LINENO"' ERR

api='http://localhost:8080'
password='AttachmentPass123!'

register_and_login() {
  local email="$1"
  local nickname="$2"
  local prefix="$3"
  curl --fail --silent --show-error -H 'Content-Type: application/json' \
    -d "{\"email\":\"$email\",\"nickname\":\"$nickname\",\"password\":\"$password\"}" \
    "$api/users" > "/tmp/${prefix}-register.json"
  curl --fail --silent --show-error -H 'Content-Type: application/json' \
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

register_and_login 'attachment-owner-smoke@example.com' 'attachmentOwner' attachment-owner
register_and_login 'attachment-worker-smoke@example.com' 'attachmentWorker' attachment-worker
register_and_login 'attachment-outsider-smoke@example.com' 'attachmentOutsider' attachment-outsider

OWNER_ID=$(json_value /tmp/attachment-owner-register.json id)
OWNER_TOKEN=$(json_value /tmp/attachment-owner-login.json accessToken)
WORKER_TOKEN=$(json_value /tmp/attachment-worker-login.json accessToken)
OUTSIDER_TOKEN=$(json_value /tmp/attachment-outsider-login.json accessToken)
CATEGORY_ID=$(docker compose exec -T db psql -U dofast -d dofast -tAc "SELECT id FROM job_categories WHERE slug='montaz-mebli' AND fulfillment_mode='ON_SITE' AND active=TRUE;" | tr -d '[:space:]')
test -n "$CATEGORY_ID"

docker compose exec -T db psql -U dofast -d dofast -v ON_ERROR_STOP=1 <<SQL
BEGIN;
UPDATE wallets SET balance = 100.00 WHERE user_id = $OWNER_ID;
INSERT INTO wallet_transactions (wallet_id, type, amount, job_id, created_at, operation_key, balance_after)
SELECT id, 'TOP_UP', 100.00, NULL, CURRENT_TIMESTAMP, 'smoke:attachments:seed:' || id, 100.00
FROM wallets WHERE user_id = $OWNER_ID;
COMMIT;
SQL

JOB=$(curl --fail --silent --show-error \
  -H "Authorization: Bearer $OWNER_TOKEN" -H 'Content-Type: application/json' \
  -d "{\"title\":\"Attachment smoke task\",\"description\":\"Task used to validate protected job attachment lifecycle.\",\"price\":40.00,\"categoryId\":$CATEGORY_ID,\"location\":{\"latitude\":51.10,\"longitude\":17.03,\"publicLabel\":\"Wrocław, centrum\",\"privateLabel\":\"Attachment smoke exact address\"}}" \
  "$api/jobs")
printf '%s' "$JOB" > /tmp/attachment-job.json
JOB_ID=$(json_value /tmp/attachment-job.json id)

python3 - <<'PY'
sig=bytes([0x89,0x50,0x4e,0x47,0x0d,0x0a,0x1a,0x0a])
open('/tmp/attachment-public.png','wb').write(sig+b'PUBLIC_ATTACHMENT_SMOKE_MARKER')
open('/tmp/attachment-participant.png','wb').write(sig+b'PARTICIPANT_ATTACHMENT_SMOKE_MARKER')
open('/tmp/attachment-secret.png','wb').write(sig+b'EXECUTION_SECRET_SMOKE_MARKER')
PY

PUBLIC=$(curl --fail --silent --show-error \
  -H "Authorization: Bearer $OWNER_TOKEN" \
  -F 'visibility=JOB_VIEWERS' -F 'file=@/tmp/attachment-public.png;type=image/png' \
  "$api/jobs/$JOB_ID/attachments")
printf '%s' "$PUBLIC" > /tmp/public-attachment.json
PUBLIC_ID=$(json_value /tmp/public-attachment.json id)

PARTICIPANT=$(curl --fail --silent --show-error \
  -H "Authorization: Bearer $OWNER_TOKEN" \
  -F 'visibility=PARTICIPANTS' -F 'file=@/tmp/attachment-participant.png;type=image/png' \
  "$api/jobs/$JOB_ID/attachments")
printf '%s' "$PARTICIPANT" > /tmp/participant-attachment.json
PARTICIPANT_ID=$(json_value /tmp/participant-attachment.json id)

SECRET=$(curl --fail --silent --show-error \
  -H "Authorization: Bearer $OWNER_TOKEN" \
  -F 'visibility=EXECUTION_SECRET' -F 'file=@/tmp/attachment-secret.png;type=image/png' \
  "$api/jobs/$JOB_ID/attachments")
printf '%s' "$SECRET" > /tmp/secret-attachment.json
SECRET_ID=$(json_value /tmp/secret-attachment.json id)
python3 -c 'import json,sys; d=json.load(sys.stdin); assert "storage" not in str(d).lower(); assert "sha256" not in d; assert d["mediaType"]=="image/png"' <<< "$SECRET"

OUTSIDER_LIST=$(curl --fail --silent --show-error -H "Authorization: Bearer $OUTSIDER_TOKEN" "$api/jobs/$JOB_ID/attachments")
python3 -c 'import json,sys; d=json.load(sys.stdin); assert len(d)==1; assert d[0]["visibility"]=="JOB_VIEWERS"' <<< "$OUTSIDER_LIST"
WORKER_LIST_BEFORE=$(curl --fail --silent --show-error -H "Authorization: Bearer $WORKER_TOKEN" "$api/jobs/$JOB_ID/attachments")
python3 -c 'import json,sys; d=json.load(sys.stdin); assert len(d)==1; assert d[0]["visibility"]=="JOB_VIEWERS"' <<< "$WORKER_LIST_BEFORE"

curl --fail --silent --show-error -H "Authorization: Bearer $OUTSIDER_TOKEN" \
  --output /tmp/public-download.png "$api/jobs/$JOB_ID/attachments/$PUBLIC_ID/content"
cmp /tmp/attachment-public.png /tmp/public-download.png

SECRET_BEFORE=$(curl --silent --output /tmp/secret-before.json --write-out '%{http_code}' \
  -H "Authorization: Bearer $WORKER_TOKEN" "$api/jobs/$JOB_ID/attachments/$SECRET_ID/content")
test "$SECRET_BEFORE" = "404"
PARTICIPANT_BEFORE=$(curl --silent --output /tmp/participant-before.json --write-out '%{http_code}' \
  -H "Authorization: Bearer $WORKER_TOKEN" "$api/jobs/$JOB_ID/attachments/$PARTICIPANT_ID/content")
test "$PARTICIPANT_BEFORE" = "404"

STORAGE_KEY=$(docker compose exec -T db psql -U dofast -d dofast -tAc "SELECT storage_key FROM job_attachments WHERE id=$SECRET_ID;" | tr -d '[:space:]')
test -n "$STORAGE_KEY"
docker compose exec -T api sh -c "test -f '/var/lib/dofast/attachments/$STORAGE_KEY.bin' && ! grep -a -q 'EXECUTION_SECRET_SMOKE_MARKER' '/var/lib/dofast/attachments/$STORAGE_KEY.bin'"

echo 'Attachment upload, metadata privacy and encrypted-at-rest storage: OK'

curl --fail --silent --show-error --output /tmp/attachment-accepted.json -X POST \
  -H "Authorization: Bearer $WORKER_TOKEN" "$api/jobs/$JOB_ID/accept"
WORKER_LIST_ACTIVE=$(curl --fail --silent --show-error -H "Authorization: Bearer $WORKER_TOKEN" "$api/jobs/$JOB_ID/attachments")
python3 -c 'import json,sys; d=json.load(sys.stdin); assert len(d)==3; assert {x["visibility"] for x in d}=={"JOB_VIEWERS","PARTICIPANTS","EXECUTION_SECRET"}' <<< "$WORKER_LIST_ACTIVE"

curl --fail --silent --show-error -H "Authorization: Bearer $WORKER_TOKEN" \
  --output /tmp/secret-download.png "$api/jobs/$JOB_ID/attachments/$SECRET_ID/content"
cmp /tmp/attachment-secret.png /tmp/secret-download.png
curl --fail --silent --show-error -H "Authorization: Bearer $WORKER_TOKEN" \
  --output /tmp/participant-download.png "$api/jobs/$JOB_ID/attachments/$PARTICIPANT_ID/content"
cmp /tmp/attachment-participant.png /tmp/participant-download.png

OUTSIDER_LIST_AFTER=$(curl --fail --silent --show-error -H "Authorization: Bearer $OUTSIDER_TOKEN" "$api/jobs/$JOB_ID/attachments")
python3 -c 'import json,sys; d=json.load(sys.stdin); assert len(d)==0' <<< "$OUTSIDER_LIST_AFTER"

curl --fail --silent --show-error --output /tmp/attachment-completion.json -X POST \
  -H "Authorization: Bearer $WORKER_TOKEN" "$api/jobs/$JOB_ID/completion"
SECRET_AFTER=$(curl --silent --output /tmp/secret-after.json --write-out '%{http_code}' \
  -H "Authorization: Bearer $WORKER_TOKEN" "$api/jobs/$JOB_ID/attachments/$SECRET_ID/content")
test "$SECRET_AFTER" = "404"
curl --fail --silent --show-error -H "Authorization: Bearer $WORKER_TOKEN" \
  --output /tmp/participant-after.png "$api/jobs/$JOB_ID/attachments/$PARTICIPANT_ID/content"
cmp /tmp/attachment-participant.png /tmp/participant-after.png
curl --fail --silent --show-error -H "Authorization: Bearer $OWNER_TOKEN" \
  --output /tmp/owner-secret-after.png "$api/jobs/$JOB_ID/attachments/$SECRET_ID/content"
cmp /tmp/attachment-secret.png /tmp/owner-secret-after.png

PUBLIC_DELETE_STATUS=$(curl --silent --output /tmp/public-delete.json --write-out '%{http_code}' -X DELETE \
  -H "Authorization: Bearer $OWNER_TOKEN" "$api/jobs/$JOB_ID/attachments/$PUBLIC_ID")
test "$PUBLIC_DELETE_STATUS" = "409"
SECRET_DELETE_STATUS=$(curl --silent --output /tmp/secret-delete.json --write-out '%{http_code}' -X DELETE \
  -H "Authorization: Bearer $OWNER_TOKEN" "$api/jobs/$JOB_ID/attachments/$SECRET_ID")
test "$SECRET_DELETE_STATUS" = "204"
docker compose exec -T api sh -c "test ! -f '/var/lib/dofast/attachments/$STORAGE_KEY.bin'"

echo 'Participant history, execution-secret revocation and deletion policy: OK'
