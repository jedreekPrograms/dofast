#!/usr/bin/env bash
set -euo pipefail
trap 'echo "Job expense smoke failed at line $LINENO"' ERR

api='http://localhost:8080'
password='ExpenseSmoke123!'

register_and_login() {
  local email="$1" nickname="$2" prefix="$3"
  curl --fail --silent --show-error -H 'Content-Type: application/json' \
    -d "{\"email\":\"$email\",\"nickname\":\"$nickname\",\"password\":\"$password\"}" \
    "$api/users" > "/tmp/${prefix}-register.json"
  curl --fail --silent --show-error -H 'Content-Type: application/json' \
    -d "{\"email\":\"$email\",\"password\":\"$password\"}" \
    "$api/users/login" > "/tmp/${prefix}-login.json"
}

json_value() {
  python3 - "$1" "$2" <<'PY'
import json,sys
with open(sys.argv[1]) as fh: value=json.load(fh)
for part in sys.argv[2].split('.'):
    value=value[int(part)] if part.isdigit() else value[part]
print(value)
PY
}

register_and_login 'expense-owner-smoke@example.com' 'expenseOwner' owner
register_and_login 'expense-worker-smoke@example.com' 'expenseWorker' worker
register_and_login 'expense-publish-smoke@example.com' 'expensePublisher' publisher
OWNER_ID=$(json_value /tmp/owner-register.json id)
WORKER_ID=$(json_value /tmp/worker-register.json id)
PUBLISHER_ID=$(json_value /tmp/publisher-register.json id)
OWNER_TOKEN=$(json_value /tmp/owner-login.json accessToken)
WORKER_TOKEN=$(json_value /tmp/worker-login.json accessToken)
PUBLISHER_TOKEN=$(json_value /tmp/publisher-login.json accessToken)
CATEGORY_ID=$(docker compose exec -T db psql -U dofast -d dofast -tAc "SELECT id FROM job_categories WHERE slug='montaz-mebli' AND fulfillment_mode='ON_SITE' AND active=TRUE;" | tr -d '[:space:]')
test -n "$CATEGORY_ID"

docker compose exec -T db psql -U dofast -d dofast -v ON_ERROR_STOP=1 <<SQL
BEGIN;
UPDATE wallets SET balance = 200.00 WHERE user_id = $OWNER_ID;
INSERT INTO wallet_transactions (wallet_id, type, amount, job_id, created_at, operation_key, balance_after)
SELECT id, 'TOP_UP', 200.00, NULL, CURRENT_TIMESTAMP, 'smoke:expense:owner-seed:' || id, 200.00 FROM wallets WHERE user_id = $OWNER_ID;
UPDATE wallets SET balance = 50.00 WHERE user_id = $PUBLISHER_ID;
INSERT INTO wallet_transactions (wallet_id, type, amount, job_id, created_at, operation_key, balance_after)
SELECT id, 'TOP_UP', 50.00, NULL, CURRENT_TIMESTAMP, 'smoke:expense:publisher-seed:' || id, 50.00 FROM wallets WHERE user_id = $PUBLISHER_ID;
COMMIT;
SQL

# Publication funding must reserve labor + expense budget, while the labor escrow fee basis remains unchanged.
PUBLICATION=$(curl --fail --silent --show-error -H "Authorization: Bearer $PUBLISHER_TOKEN" -H 'Content-Type: application/json' \
  -d "{\"requestId\":\"expense-budget-smoke\",\"job\":{\"title\":\"Expense publication smoke\",\"description\":\"Validates publication funding includes a separate shopping budget.\",\"price\":40.00,\"expenseBudget\":100.00,\"categoryId\":$CATEGORY_ID,\"location\":{\"latitude\":51.10,\"longitude\":17.03,\"publicLabel\":\"Wrocław, centrum\",\"privateLabel\":\"Expense publication exact address\"}}}" \
  "$api/jobs/publications")
printf '%s' "$PUBLICATION" > /tmp/expense-publication.json
PUBLICATION_ID=$(json_value /tmp/expense-publication.json id)
python3 - <<'PY'
import json
p=json.load(open('/tmp/expense-publication.json'))
assert p['status']=='PAYMENT_REQUIRED', p
assert float(p['totalAmount'])==140.0, p
assert float(p['walletReservedAmount'])==50.0, p
assert float(p['missingAmount'])==90.0, p
assert float(p['paymentAmount'])==90.0, p
PY
curl --fail --silent --show-error -X POST -H "Authorization: Bearer $PUBLISHER_TOKEN" \
  "$api/jobs/publications/$PUBLICATION_ID/cancel" >/tmp/expense-publication-cancel.json
PUBLISHER_BALANCE=$(docker compose exec -T db psql -U dofast -d dofast -tAc "SELECT balance FROM wallets WHERE user_id=$PUBLISHER_ID;" | tr -d '[:space:]')
test "$PUBLISHER_BALANCE" = "50.00"

JOB=$(curl --fail --silent --show-error -H "Authorization: Bearer $OWNER_TOKEN" -H 'Content-Type: application/json' \
  -d "{\"title\":\"Receipt reimbursement smoke\",\"description\":\"Buy the requested items and attach the receipt for reimbursement.\",\"price\":40.00,\"expenseBudget\":100.00,\"categoryId\":$CATEGORY_ID,\"location\":{\"latitude\":51.10,\"longitude\":17.03,\"publicLabel\":\"Wrocław, centrum\",\"privateLabel\":\"Expense smoke exact address\"}}" \
  "$api/jobs")
printf '%s' "$JOB" > /tmp/expense-job.json
JOB_ID=$(json_value /tmp/expense-job.json id)
python3 - <<'PY'
import json
j=json.load(open('/tmp/expense-job.json'))
assert float(j['price'])==40.0, j
assert float(j['expenseBudget'])==100.0, j
PY
OWNER_BALANCE_AFTER_LOCK=$(docker compose exec -T db psql -U dofast -d dofast -tAc "SELECT balance FROM wallets WHERE user_id=$OWNER_ID;" | tr -d '[:space:]')
test "$OWNER_BALANCE_AFTER_LOCK" = "60.00"

docker compose exec -T db psql -U dofast -d dofast -tAc "SELECT budget_amount || ':' || claimed_amount || ':' || status FROM job_expense_escrows WHERE job_id=$JOB_ID;" | tr -d '[:space:]' | grep -qx '100.00:0.00:HELD'

curl --fail --silent --show-error -X POST -H "Authorization: Bearer $WORKER_TOKEN" "$api/jobs/$JOB_ID/accept" >/tmp/expense-accept.json
python3 - <<'PY'
sig=bytes([0x89,0x50,0x4e,0x47,0x0d,0x0a,0x1a,0x0a])
open('/tmp/expense-receipt.png','wb').write(sig+b'EXPENSE_RECEIPT_SMOKE')
PY
RECEIPT=$(curl --fail --silent --show-error -H "Authorization: Bearer $WORKER_TOKEN" \
  -F 'visibility=PARTICIPANTS' -F 'file=@/tmp/expense-receipt.png;type=image/png' \
  "$api/jobs/$JOB_ID/attachments")
printf '%s' "$RECEIPT" > /tmp/expense-receipt.json
RECEIPT_ID=$(json_value /tmp/expense-receipt.json id)

CLAIM=$(curl --fail --silent --show-error -H "Authorization: Bearer $WORKER_TOKEN" -H 'Content-Type: application/json' \
  -d "{\"amount\":35.00,\"attachmentId\":$RECEIPT_ID}" "$api/jobs/$JOB_ID/expenses/claims")
printf '%s' "$CLAIM" > /tmp/expense-claim.json
python3 - "$RECEIPT_ID" "$WORKER_ID" <<'PY'
import json,sys
c=json.load(open('/tmp/expense-claim.json'))
assert float(c['amount'])==35.0, c
assert c['attachmentId']==int(sys.argv[1]), c
assert c['workerId']==int(sys.argv[2]), c
PY

SUMMARY=$(curl --fail --silent --show-error -H "Authorization: Bearer $OWNER_TOKEN" "$api/jobs/$JOB_ID/expenses")
printf '%s' "$SUMMARY" > /tmp/expense-summary.json
python3 - <<'PY'
import json
s=json.load(open('/tmp/expense-summary.json'))
assert s['status']=='HELD', s
assert float(s['budgetAmount'])==100.0, s
assert float(s['claimedAmount'])==35.0, s
assert len(s['claims'])==1, s
PY

curl --fail --silent --show-error -X POST -H "Authorization: Bearer $WORKER_TOKEN" "$api/jobs/$JOB_ID/completion" >/tmp/expense-completion.json
curl --fail --silent --show-error -X POST -H "Authorization: Bearer $OWNER_TOKEN" "$api/jobs/$JOB_ID/confirm" >/tmp/expense-confirm.json

FINAL=$(curl --fail --silent --show-error -H "Authorization: Bearer $WORKER_TOKEN" "$api/jobs/$JOB_ID/expenses")
printf '%s' "$FINAL" > /tmp/expense-final.json
python3 - <<'PY'
import json
s=json.load(open('/tmp/expense-final.json'))
assert s['status']=='SETTLED', s
assert float(s['budgetAmount'])==100.0, s
assert float(s['claimedAmount'])==35.0, s
assert float(s['reimbursedAmount'])==35.0, s
assert float(s['refundedAmount'])==65.0, s
PY

EXPENSE_LOCK=$(docker compose exec -T db psql -U dofast -d dofast -tAc "SELECT amount FROM wallet_transactions WHERE job_id=$JOB_ID AND type='EXPENSE_BUDGET_LOCK';" | tr -d '[:space:]')
EXPENSE_REIMBURSE=$(docker compose exec -T db psql -U dofast -d dofast -tAc "SELECT amount FROM wallet_transactions WHERE job_id=$JOB_ID AND type='EXPENSE_REIMBURSEMENT';" | tr -d '[:space:]')
EXPENSE_REFUND=$(docker compose exec -T db psql -U dofast -d dofast -tAc "SELECT amount FROM wallet_transactions WHERE job_id=$JOB_ID AND type='EXPENSE_BUDGET_REFUND';" | tr -d '[:space:]')
LABOR_FEE=$(docker compose exec -T db psql -U dofast -d dofast -tAc "SELECT platform_fee_amount FROM escrow_transactions WHERE job_id=$JOB_ID;" | tr -d '[:space:]')
test "$EXPENSE_LOCK" = "-100.00"
test "$EXPENSE_REIMBURSE" = "35.00"
test "$EXPENSE_REFUND" = "65.00"
test "$LABOR_FEE" = "0.40"

echo 'Expense publication funding, receipt claim, fee-free reimbursement and unused-budget refund: OK'
