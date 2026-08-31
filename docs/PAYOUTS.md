# Worker payouts

## Purpose

The payout module converts **withdrawable worker earnings** into an auditable cash-out request. It is deliberately separate from job settlement: a completed job first credits the worker wallet, then a later payout request reserves only funding lots whose provenance is explicitly withdrawable.

Stripe/card funding, legacy balance and platform adjustments are not silently converted into cash-outable value. `docs/WALLET_SOURCE_OF_FUNDS.md` is the canonical source-of-funds policy.

## User flow

`GET /wallet/payouts/eligibility` returns payout eligibility, withdrawable balance, minimum amount, provider mode and recipient readiness. `POST /wallet/payouts` requires an idempotent client `requestId`.

A successful request:

1. locks the user and requires an ACTIVE account;
2. requires current identity status `VERIFIED`;
3. requires an available payout provider;
4. for Stripe Connect, refreshes the authoritative connected-account state before reserving money;
5. requires sufficient `withdrawable=true` funding lots;
6. persists the payout and audit event;
7. creates an idempotent `PAYOUT_RESERVE` consuming only eligible funding lots.

Repeating the same request id and amount returns the same payout without reserving funds again. Reusing the request id for another amount is rejected. A user may cancel only a still-queued `REQUESTED` payout; cancellation restores the exact reserved funding movements with `PAYOUT_RESTORE`.

## State machine

```text
REQUESTED -> PROCESSING -> PAID          (synchronous provider only)
    |            |
    |            +-> SUBMITTED -> PAID   (asynchronous provider settlement)
    |            |       |
    |            |       +-------> FAILED + PAYOUT_RESTORE
    |            |
    |            +-> REQUESTED       (safe retryable provider failure)
    |            +-> REVIEW_REQUIRED (ambiguous result / retry exhaustion)
    |            +-> FAILED          (definitive pre-acceptance failure + restore)
    |
    +-> CANCELLED (user cancellation before processing + restore)

REVIEW_REQUIRED -> REQUESTED (audited admin retry, only when provider outcome is safely retryable)
REVIEW_REQUIRED -> FAILED    (audited admin rejection + restore, only when money location is unambiguous)
```

`SUBMITTED` means the provider accepted the external operation and returned a durable payout reference. It is intentionally non-terminal. A submitted payout is never returned to the normal dispatch queue merely because settlement is slow.

A special `REVIEW_REQUIRED` with failure code `STRIPE_IDEMPOTENCY_WINDOW_EXPIRED` is also intentionally non-retryable through ordinary admin actions. Neither admin retry nor wallet restoration is allowed until the previous Stripe Transfer/Payout is externally reconciled. This prevents both duplicate external money movement and creation of spendable wallet value while money may already have left the platform.

## Provider boundary and idempotency

`PayoutProvider` receives a stable idempotency key derived from the local payout id. Stripe Connect derives two independent keys from it:

- `payout:{id}:provider:transfer` for the platform `Transfer`;
- `payout:{id}:provider:payout` for the connected-account `Payout`.

Stripe API v1 can prune idempotency keys after they are at least 24 hours old. Reusing a pruned key can execute a new POST. doFast therefore treats an ambiguous Stripe Connect `PROCESSING` attempt as automatically retryable only while its `processing_started_at` is inside a conservative **23-hour safety window**.

The stale-processing scheduler locks the row with `FOR UPDATE SKIP LOCKED`. Inside the safe window it can return the request to `REQUESTED` with the same provider keys. At or beyond the 23-hour cutoff it instead moves the request to `REVIEW_REQUIRED` with `STRIPE_IDEMPOTENCY_WINDOW_EXPIRED`, keeps the wallet reserve held, does not call Stripe again, and records an immutable review event.

This rule applies regardless of whether the crash occurred after the platform Transfer or after the connected-account Payout. It closes the long-downtime duplicate-money-movement window that ordinary short-lived idempotent retries cannot cover.

## Stripe Connect live dispatch

Stripe Connect onboarding, live dispatch and submitted reconciliation have independent kill switches:

- `PAYOUT_STRIPE_CONNECT_ENABLED=true` permits Express account provisioning/refresh;
- `PAYOUT_STRIPE_CONNECT_DISPATCH_ENABLED=true` permits new external money movement;
- `PAYOUT_STRIPE_CONNECT_RECONCILIATION_ENABLED=true` permits read-only reconciliation of already-created Stripe payouts.

Before every live dispatch, doFast refreshes the connected account and requires submitted details, payouts enabled, an active `transfers` capability and no currently-due/past-due requirements. The connected account payout schedule is forced to `manual` before money movement.

Live dispatch then performs two distinct operations:

1. an idempotent Stripe `Transfer` moves the exact reserved amount from the platform to the mapped connected account;
2. only after the Transfer id is durably stored as `provider_transfer_reference`, an idempotent manual Stripe `Payout` is created on that connected account.

Short crash/retry cycles reuse the same Stripe operation and validate amount, currency, destination/account and doFast metadata. Long ambiguous cycles are quarantined before Stripe's documented idempotency-retention boundary instead of assuming the old key still exists.

A created Stripe Payout always maps to doFast `SUBMITTED`; synchronous API status is not authoritative for wallet settlement. Signed Stripe events remain the preferred terminal authority.

### Crash after Transfer creation

If Stripe accepted the Transfer but the process died before `provider_transfer_reference` committed, doFast has no durable external Transfer id. Automatic retry is allowed only inside the 23-hour safety window. After that window, the payout is quarantined and ordinary admin retry/restore is blocked until an operator reconciles the provider state externally.

### Crash after Payout creation

If the Transfer reference was already committed, Stripe created the connected-account Payout, and the process then died before local `provider_reference` was saved, a later **signed terminal Stripe payout event** can self-heal the missing reference.

The settlement path first tries the normal unique `(provider_code, provider_reference)` lookup. If that misses, it may fall back to signed Stripe metadata `dofastPayoutId`, lock that local payout by id and accept the missing reference only when all invariants match:

- provider is exactly `stripe-connect`;
- local state is an ambiguous `PROCESSING` or `REVIEW_REQUIRED` state;
- exact doFast payout id;
- exact doFast user id;
- exact amount and currency;
- exact mapped connected Stripe account;
- exact previously stored platform Transfer reference.

Only after those checks does doFast attach the Stripe payout id, transition through `SUBMITTED`, and reuse the normal terminal settlement path. Mismatched or foreign metadata cannot attach an external payout to a local request.

## Submitted payout reconciliation

Once `SUBMITTED`, `next_attempt_at` is reused as a read-only reconciliation lease. A scheduler claims due rows with `FOR UPDATE SKIP LOCKED`, advances the lease, then retrieves the **existing** Stripe payout by its stored provider reference. Reconciliation never creates a replacement Payout and never calls the normal dispatch provider.

The response must match payout id, amount, currency, user metadata, Transfer reference and connected account. Provider states are handled conservatively:

- `pending` / `in_transit` -> remain `SUBMITTED`;
- `paid` -> terminal paid settlement;
- `failed` / `canceled` -> terminal failure settlement after safe provider-fund recovery;
- unknown state, identity mismatch or provider-read failure -> remain `SUBMITTED`, retain the reserve and retry only the future read.

## Signed payout settlement and transfer reversal

The signed `/webhooks/stripe` endpoint handles connected-account payout events. `event.created` ordering prevents stale terminal events from regressing newer provider state, and contradictory terminal transitions fail closed.

For `PAID`, no second wallet debit is created because `PAYOUT_RESERVE` already removed the money from spendable balance.

For `FAILED` or `CANCELED`, failed payout funds return to the connected Stripe balance rather than automatically to the doFast platform. doFast therefore validates and reverses the original platform Transfer first. Only after that provider recovery succeeds may local settlement move to `FAILED` and execute the idempotent `PAYOUT_RESTORE`.

External transfer reversal is preflighted against local state before the provider call. Repeated already-failed events do not reverse money again. If transfer recovery is unavailable or ambiguous, processing fails and the wallet reservation remains held.

## Administration

`/admin/payouts/**` is admin-gated. The admin DTO/UI exposes private provider payout and Transfer references for operator reconciliation; ordinary user DTOs do not.

For ordinary `REVIEW_REQUIRED` cases the admin can use audited retry or audited final rejection according to backend rules. For `STRIPE_IDEMPOTENCY_WINDOW_EXPIRED`, both actions are hidden in the UI and rejected by the backend. The operator must inspect Stripe first because either a Transfer or a Payout may already exist.

A submitted payout with reconciliation errors is likewise an in-flight provider operation, not a candidate for force-retry through the dispatch queue.

## Wallet accounting

- `PAYOUT_RESERVE` is a negative wallet entry and consumes only withdrawable funding lots.
- Worker `EARNED_JOB` funding is withdrawable.
- `STRIPE_PAYMENT`, `LEGACY_UNVERIFIED` and `PLATFORM_ADJUSTMENT` are not withdrawable.
- `PAYOUT_RESTORE` restores the exact funding movements consumed by the original reserve.
- `SUBMITTED` creates no additional wallet debit.
- terminal `PAID` creates no additional wallet debit.
- terminal failure restores wallet value only after external provider money is safely recovered or provider rejection is definitive before acceptance.

For every wallet, `wallet.balance` must equal the sum of remaining funding lots. A provenance mismatch is a financial-accounting error and fails closed.

## KYC and account safety

Payout creation and dispatch require a currently ACTIVE user and `VERIFIED` identity. The dispatcher rechecks eligibility immediately before external money movement. Stripe Express account creation is also gated by the same account/verification boundary.

Provider account ids, Transfer ids, Payout ids, payout amounts and provider failure details are private finance/operator data and never appear in public profiles.

## Database and CI

Flyway migrations own payout persistence and provider references. This crash-window hardening requires **no new migration**: it uses the existing `processing_started_at`, `failure_code`, `provider_transfer_reference` and `provider_reference` fields.

`Worker payout smoke` runs against PostgreSQL and covers sandbox reservation/idempotency/cancellation/settlement plus a scheduler scenario that inserts an old Stripe Connect `PROCESSING` row. The smoke requires it to become exactly `REVIEW_REQUIRED|STRIPE_IDEMPOTENCY_WINDOW_EXPIRED`, with the original attempt count unchanged, no provider references invented and no `PAYOUT_RESTORE` created.

Unit tests additionally cover:

- recent stale Stripe Connect dispatch remains safely retryable;
- dispatch older than the safety window is quarantined;
- admin cannot bypass quarantine with retry or wallet restoration;
- a signed terminal Stripe event can recover a provider payout reference lost after provider acceptance;
- wrong signed-event identity metadata cannot attach an external payout;
- stale/contradictory payout events cannot regress financial state;
- failed payouts reverse the Transfer before wallet restoration;
- submitted reconciliation remains read-only.

Real Stripe Connect test-mode end-to-end validation with an Express account remains a separate launch gate. Only that can prove actual provider account creation, Transfer, connected-account Payout, webhook delivery and retrieval against Stripe's test environment.

## Related publication-payment flow

Job publication may combine wallet value and Stripe funding. Publication reservations, redirects and settlement remain independent of worker cash-out. Cancellation restores the same source lots that funded the original reservation, and returned card-funded value remains non-withdrawable.
