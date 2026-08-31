#!/usr/bin/env bash
set -euo pipefail
trap 'echo "Job publication PaymentIntent create-recovery schema smoke failed at line $LINENO"' ERR

COLUMN_COUNT=$(docker compose exec -T db psql -U dofast -d dofast -tAc "
SELECT count(*)
FROM information_schema.columns
WHERE table_name = 'job_publications'
  AND column_name IN (
    'stripe_create_started_at',
    'stripe_create_attempt_count',
    'stripe_create_next_attempt_at',
    'stripe_create_review_required',
    'stripe_create_last_error'
  );" | tr -d '[:space:]')
test "$COLUMN_COUNT" = "5"

ATTEMPT_COLUMN_OK=$(docker compose exec -T db psql -U dofast -d dofast -tAc "
SELECT is_nullable = 'NO' AND column_default = '0'
FROM information_schema.columns
WHERE table_name='job_publications' AND column_name='stripe_create_attempt_count';" | tr -d '[:space:]')
test "$ATTEMPT_COLUMN_OK" = "t"

REVIEW_COLUMN_OK=$(docker compose exec -T db psql -U dofast -d dofast -tAc "
SELECT is_nullable = 'NO' AND column_default = 'false'
FROM information_schema.columns
WHERE table_name='job_publications' AND column_name='stripe_create_review_required';" | tr -d '[:space:]')
test "$REVIEW_COLUMN_OK" = "t"

CONSTRAINT_DEF=$(docker compose exec -T db psql -U dofast -d dofast -tAc "
SELECT pg_get_constraintdef(oid)
FROM pg_constraint
WHERE conname='chk_job_publications_stripe_create_attempt_count';")
echo "$CONSTRAINT_DEF" | grep -q 'stripe_create_attempt_count >= 0'

INDEX_DEF=$(docker compose exec -T db psql -U dofast -d dofast -tAc "
SELECT indexdef
FROM pg_indexes
WHERE indexname='idx_job_publications_stripe_create_recovery_due';")
echo "$INDEX_DEF" | grep -q 'stripe_create_next_attempt_at'
echo "$INDEX_DEF" | grep -q "status = 'CANCELLED'"
echo "$INDEX_DEF" | grep -q 'stripe_payment_intent_id IS NULL'
echo "$INDEX_DEF" | grep -q 'stripe_create_started_at IS NOT NULL'
echo "$INDEX_DEF" | grep -q 'stripe_create_review_required = false'

echo 'Publication PaymentIntent create-recovery V57 schema: OK'
