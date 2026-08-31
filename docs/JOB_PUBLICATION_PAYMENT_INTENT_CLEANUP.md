# Job publication PaymentIntent cleanup recovery

## Problem boundary

A job publication is cancelled locally before doFast tries to cancel its Stripe PaymentIntent. This ordering is intentional: wallet reservation release and the terminal local `CANCELLED` state must not be rolled back by a transient Stripe outage.

The old implementation performed only one best-effort provider call after that local transaction committed. A process crash in the narrow window between the local commit and `PaymentIntent.cancel()` permanently lost the cleanup attempt because the expiry scheduler only selected `PAYMENT_REQUIRED` publications. Late-payment settlement remained financially safe, but Stripe could still hold an active PaymentIntent for a publication that was already cancelled.

## Durable cleanup contract

Cancellation and expiry now persist provider-cleanup work in `job_publications` in the same PostgreSQL transaction that commits `CANCELLED` and restores the wallet reservation.

The persisted state contains:

- cleanup attempt count;
- next-attempt/lease timestamp;
- completion timestamp;
- review-required quarantine flag;
- last failure code.

Flyway `V56__job_publication_payment_intent_cleanup_recovery.sql` also backfills historical cancelled publications that have a Stripe PaymentIntent but no recorded successful payment. Historical cancelled rows that already recorded a successful late payment are marked cleanup-complete because the PaymentIntent is no longer cancellable.

## Claim, provider call and completion

The worker intentionally separates database ownership from the network request:

1. Read a bounded list of due publication ids.
2. Lock one publication with `PESSIMISTIC_WRITE`.
3. Re-check that it is still cancelled, unpaid, incomplete and due.
4. Increment the attempt count and advance `nextAttemptAt` by a two-minute lease.
5. Commit the claim transaction.
6. Retrieve the authoritative PaymentIntent from Stripe without holding a database lock.
7. If the intent is still cancellable, request cancellation.
8. In a new short transaction, mark cleanup complete or schedule a capped-backoff retry.

Two application instances may observe the same due-id snapshot, but only one can claim the row. The second instance waits for the row lock, re-reads the advanced lease and performs no provider call.

## Crash/restart behavior

### Crash after local cancellation commit but before provider call

The durable cleanup row is already due. The scheduled cleanup worker discovers it after restart and claims it normally.

### Crash after cleanup claim but before provider call

The lease remains in PostgreSQL. After two minutes it becomes due again. A later worker reclaims the row and reads Stripe state before deciding what to do.

### Stripe cancellation succeeds, then the process crashes before local completion

After the lease expires, retry retrieves the PaymentIntent. `canceled` is authoritative terminal state, so the worker marks the local cleanup complete and does not attempt a second cancellation.

### Payment succeeds before cleanup wins the race

A signed successful-payment webhook remains authoritative. For an already cancelled publication it credits/claims the payment through the normal transaction-safe settlement path, records `CANCELLED_BEFORE_PAYMENT_CONFIRMED`, leaves the publication cancelled and marks cleanup complete. A provider read that already reports `succeeded` also stops cancellation attempts; webhook retry/reconciliation remains responsible for local payment settlement.

## Failure policy

Provider exceptions and unexpected transient states schedule exponential backoff capped at five minutes. Each claim is counted. After eight unsuccessful claimed attempts the publication is quarantined with `stripe_cleanup_review_required=true` rather than generating an unbounded provider-call loop.

Successful cleanup clears `stripe_cleanup_last_error`. The failure field is never overloaded with success/status data.

This review-required state should be surfaced by the broader finance/operations observability work before production launch.

## Invariants

- Local cancellation and reservation restoration remain authoritative even when Stripe is unavailable.
- No database transaction or row lock is held across a Stripe network call.
- A process restart cannot lose provider-cleanup work.
- A stale scheduler snapshot cannot create two concurrent cleanup calls.
- Provider state is re-read after ambiguous/crashed attempts.
- A successful late payment never resurrects a cancelled publication.
- Cleanup failure never refunds, credits or debits money by itself.
