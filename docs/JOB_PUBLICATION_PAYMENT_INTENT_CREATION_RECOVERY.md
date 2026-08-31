# Job publication PaymentIntent creation recovery

## Problem

A payment-required job publication must create a Stripe PaymentIntent before the browser can render Payment Element. Before V57, `JobPublicationPaymentIntentService` held a pessimistic database lock and one Spring transaction while calling Stripe. That created two production risks:

1. a slow or unavailable provider kept a database row lock open across the network call;
2. the process could die after Stripe successfully created the PaymentIntent but before `stripe_payment_intent_id` committed locally.

The second case could leave an active provider object that the application no longer knew by id. If the publication was then cancelled or expired, the V56 cancellation-cleanup queue could not cancel that orphan because it only operates on a known `stripe_payment_intent_id`.

## Transaction boundary

Creation is now split into three phases:

1. **Prepare / claim** — a short database transaction locks the publication, verifies owner/status/expiry, persists the first `stripe_create_started_at`, increments the attempt counter and leases the create attempt for two minutes.
2. **Provider call** — Stripe is called only after the prepare transaction has committed. No database transaction or pessimistic row lock spans this network request.
3. **Finalize** — a second short transaction re-locks the publication and attaches the authoritative PaymentIntent id after validating provider identity, amount, currency and metadata.

The Stripe idempotency key remains deterministic: `dofast:job-publication:<publicationId>`.

## Cancellation and expiry races

A publication can be cancelled or expire while Stripe creation is in flight. Local cancellation still restores the reserved wallet source immediately; it never waits for Stripe.

Cancellation does not steal an active create lease. If the original provider call returns normally, finalize attaches the PaymentIntent id even though the publication is already `CANCELLED`. Attaching an id to a cancelled publication automatically arms the existing V56 durable PaymentIntent cleanup queue, and the immediate path also asks that queue to process the row.

If the process dies before finalize, the create lease eventually expires and the V57 recovery scheduler can claim the orphan-creation work.

If the provider response arrives after the publication payment webhook has already settled, the webhook remains authoritative. The signed webhook path already locks the publication, validates the same payment identity and can attach a missing PaymentIntent id before settlement. A later create finalize sees the settled state and does not recreate or publish anything.

## Restart recovery and the 23-hour boundary

Stripe API v1 idempotency records are not an indefinite datastore. A key can be removed after it has been retained for at least 24 hours. Replaying a create request after that point can therefore create a new PaymentIntent instead of returning the original object.

For that reason doFast persists the time of the **first** provider-create attempt and automatically replays the deterministic create request only for 23 hours from `stripe_create_started_at`.

Within that safety window:

- if the original Stripe request succeeded before the process crashed, replay returns the same PaymentIntent and recovers its id;
- if the original request never reached Stripe, replay may create a PaymentIntent, but the publication is already cancelled and the newly known object is immediately handed to durable V56 cleanup.

After 23 hours the application does **not** replay provider creation. The row becomes `stripe_create_review_required` with `IDEMPOTENCY_WINDOW_EXPIRED`. This is intentionally fail-closed: avoiding a duplicate chargeable provider object is more important than automatic cleanup at that point.

## Retry and multi-instance behavior

Create recovery uses the same operational pattern as other durable provider work:

- due ids are discovered in bounded batches;
- the row is re-read under a pessimistic lock before claim;
- a two-minute lease prevents concurrent provider calls from normal application instances;
- failed attempts use capped exponential backoff;
- after eight unresolved attempts the row is quarantined for review;
- one broken row does not stop the rest of a scheduler tick.

The provider call always happens after the claim transaction commits.

## Provider identity validation

Neither normal creation nor orphan recovery attaches a returned provider id until Stripe data matches the durable publication:

- PaymentIntent id must be present;
- `purpose=JOB_PUBLICATION`;
- `userId` must equal the publication owner;
- `jobPublicationId` must equal the publication id;
- amount must exactly equal the server-computed publication payment amount;
- currency must match.

A mismatched provider object is quarantined before local attachment.

## Historical rows before V57

V57 intentionally does **not** backfill `stripe_create_started_at` for old cancelled publications that have no local PaymentIntent id.

Old state cannot distinguish these cases:

- Stripe creation succeeded and the local process crashed before saving the id;
- Stripe creation was never attempted;
- Stripe returned an error before creating anything.

Automatically marking every historical cancellation as replayable could therefore create new provider PaymentIntents rather than recover existing ones. Historical investigations, if needed, must be performed from provider-side Stripe records/metadata instead of an unsafe automatic replay.

## Schema

Flyway `V57__job_publication_payment_intent_create_recovery.sql` adds:

- `stripe_create_started_at`;
- `stripe_create_attempt_count`;
- `stripe_create_next_attempt_at`;
- `stripe_create_review_required`;
- `stripe_create_last_error`;
- a non-negative attempt-count constraint;
- a partial due-work index for cancelled publications with a known started create attempt but no locally attached PaymentIntent id.

## Validation

The change is covered by focused tests for:

- durable create claim before provider invocation;
- active-lease concurrency rejection;
- cancellation while the provider call is in flight;
- attachment plus V56 cleanup after that race;
- provider failure retry;
- provider identity mismatch quarantine;
- recovery after restart;
- fail-closed behavior beyond the safe idempotency replay window;
- scheduler isolation;
- real PostgreSQL V57 schema/index/constraint verification in the publication payment smoke workflow.
