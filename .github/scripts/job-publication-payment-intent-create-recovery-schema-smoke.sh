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

CONSTRAINT_OK=$(docker compose exec -T db psql -U dofast -d dofast -tAc "
SELECT position('stripe_create_attempt_count' in pg_get_constraintdef(oid)) > 0
   AND position('>= 0' in pg_get_constraintdef(oid)) > 0
FROM pg_constraint
WHERE conname='chk_job_publications_stripe_create_attempt_count';" | tr -d '[:space:]')
test "$CONSTRAINT_OK" = "t"

INDEX_OK=$(docker compose exec -T db psql -U dofast -d dofast -tAc "
SELECT
    position('stripe_create_next_attempt_at' in pg_get_indexdef(i.indexrelid)) > 0
AND position('stripe_create_started_at' in pg_get_expr(i.indpred, i.indrelid)) > 0
AND position('stripe_payment_intent_id' in pg_get_expr(i.indpred, i.indrelid)) > 0
AND position('stripe_create_review_required' in pg_get_expr(i.indpred, i.indrelid)) > 0
AND position('payment_received_at' in pg_get_expr(i.indpred, i.indrelid)) > 0
AND position('CANCELLED' in pg_get_expr(i.indpred, i.indrelid)) > 0
FROM pg_index i
JOIN pg_class idx ON idx.oid = i.indexrelid
WHERE idx.relname='idx_job_publications_stripe_create_recovery_due';" | tr -d '[:space:]')
test "$INDEX_OK" = "t"

echo 'Publication PaymentIntent create-recovery V57 schema: OK'
