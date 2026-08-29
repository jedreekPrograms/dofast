# Worker payouts

## Purpose

The payout module turns **withdrawable wallet earnings** into an auditable cash-out request. It is deliberately separate from job escrow settlement: a completed job first credits the worker wallet (after the snapshotted platform fee), and a later payout request reserves part of the wallet value that is explicitly classified as withdrawable.

Wallet balance is not anonymous cash. Refunded escrow, Stripe top-ups, legacy balance and platform adjustments retain their original source-of-funds classification. Returning value to a wallet makes it spendable again inside doFast, but does not automatically make it eligible for cash-out. The canonical source policy is documented in `docs/WALLET_SOURCE_OF_FUNDS.md`.

## User flow

`GET /wallet/payouts/eligibility` returns the current user's payout eligibility, balance, minimum amount, provider mode, and connected-recipient readiness. Stripe Connect setup readiness is reported independently of whether live payout dispatch is enabled. Payout eligibility uses the wallet's withdrawable funding balance rather than treating total wallet balance as cash-outable.

`GET /wallet/payouts/onboarding/status` reads the last authoritative connected-account state cached by doFast. `POST /wallet/payouts/onboarding/refresh` re-reads provider state. `POST /wallet/payouts/onboarding/link` creates or reuses the user's mapped Express account and returns a single-use Stripe-hosted onboarding link. Provisioning is available only to ACTIVE users whose doFast identity verification is currently `VERIFIED`. Account creation is serialized by a fresh pessimistic user lock so concurrent onboarding requests cannot race past the local one-account-per-provider invariant.

Stripe `refresh_url` returns to the wallet with `stripe-connect=refresh`; the web app immediately requests a fresh single-use Account Link instead of trying to reuse an expired URL. The normal return path refreshes provider state before presenting readiness.

`POST /wallet/payouts` requires an idempotent client `requestId`. A successful request:

1. pessimistically locks the user and verifies the account is ACTIVE;
2. requires current identity-verification status `VERIFIED`;
3. requires a configured payout provider;
4. if the configured provider is Stripe Connect, requires Connect onboarding to be available and refreshes the authoritative Stripe account state immediately before any wallet reservation;
5. verifies that the requested amount is covered by funding lots marked `withdrawable=true`;
6. persists the payout request and audit event;
7. debits only eligible funding lots using `PAYOUT_RESERVE`, making double-spending impossible while the transfer is pending.

Repeating the same request id and amount returns the same payout without reserving funds again. Reusing the request id for another amount is rejected.

A user may cancel only a still-queued `REQUESTED` payout. Cancellation writes immutable audit events and restores the reserved amount exactly once with `PAYOUT_RESTORE`. That restoration returns value to the exact funding lots consumed by the reserve and preserves their source type and withdrawability.

## State machine

```text
REQUESTED -> PROCESSING -> PAID          (synchronous provider only)
    |            |
    |            +-> SUBMITTED -> PAID   (asynchronous provider settlement)
    |            |       |
    |            |       +-------> FAILED + PAYOUT_RESTORE
    |            |
    |            +-> REQUESTED       (retryable provider failure before acceptance)
    |            +-> REVIEW_REQUIRED (ambiguous result / retry exhaustion before acceptance)
    |            +-> FAILED          (definitive pre-acceptance failure + funds restored)
    |
    +-> CANCELLED (user cancels before processing + funds restored)

REVIEW_REQUIRED -> REQUESTED (audited admin retry)
REVIEW_REQUIRED -> FAILED    (audited admin decision + funds restored)
```

`SUBMITTED` is intentionally non-terminal. It means a provider has accepted the payout operation and returned a durable reference, but doFast has not yet received authoritative terminal settlement. A submitted payout is never automatically retried or restored merely because settlement is slow. This prevents a second transfer or bank payout from being created while the first might still reach the recipient.

An ambiguous pre-acceptance provider result never restores funds automatically. Returning the money while the provider might still have sent it could create a double payout, so such cases move to `REVIEW_REQUIRED` for audited operator action.

## Provider boundary and idempotency

`PayoutProvider` receives a stable provider idempotency key derived from the payout id. Retries reuse the same key. Synchronous providers can return terminal success. Asynchronous providers return a durable reference with `PayoutDispatchResult.submitted(...)`; the request moves to `SUBMITTED` and retains its original wallet reservation until a terminal provider settlement is applied.

`PayoutProviderSettlementService` is the provider-neutral settlement boundary. It locks the payout by the unique `(provider_code, provider_reference)` pair and deduplicates provider notifications by `(provider_code, provider_event_id)`. A `PAID` settlement finalizes without another wallet debit. A definitive `FAILED` settlement restores the reserved amount through the existing idempotent `PAYOUT_RESTORE` operation. Replayed events cannot mutate money twice, and a terminal event contradicting an already terminal state is rejected rather than silently rewriting financial history.

The application defaults to `PAYOUT_PROVIDER=disabled` unless configured. `sandbox` is allowed only when `PAYOUT_SANDBOX_ENABLED=true`; it is for local development and CI and **never sends real money**. The sandbox provider remains synchronous so smoke coverage still ends directly in `PAID`.

## Stripe Connect live dispatch

Stripe Connect recipient onboarding and live money movement use two independent kill switches:

- `PAYOUT_STRIPE_CONNECT_ENABLED=true` permits verified ACTIVE users to provision/refresh their Express recipient account;
- `PAYOUT_STRIPE_CONNECT_DISPATCH_ENABLED=true` registers the live `stripe-connect` payout provider.

`PAYOUT_PROVIDER=stripe-connect` is therefore still fail-closed unless the dispatch switch is explicitly enabled. Enabling onboarding alone cannot move money.

Before every live dispatch, doFast performs a fresh Stripe account-state read and requires submitted account details, payouts enabled, an active `transfers` capability, and no currently-due or past-due requirements. It then explicitly sets the connected account payout schedule to `manual` before moving funds. This prevents Stripe's default automatic payout schedule from racing the payout lifecycle controlled by doFast.

Live Stripe Connect dispatch has two distinct provider operations:

1. an idempotent Stripe `Transfer` moves the exact reserved amount from the platform balance to the user's connected account;
2. after that transfer id is durably recorded in `payout_requests.provider_transfer_reference`, an idempotent manual Stripe `Payout` is created on the connected account.

The two operations use stable, different provider idempotency keys derived from the doFast payout id. A crash after Stripe accepted the transfer but before a later attempt cannot send the platform transfer twice: the retry resolves the same provider operation, validates amount/currency/destination/metadata, persists the transfer reference, and only then proceeds to the connected-account payout.

A created Stripe Payout always maps to doFast `SUBMITTED`, even if the create response appears terminal. Browser responses and synchronous API response status are not used to release or restore wallet funds. Terminal settlement is driven by signed Stripe webhook events.

The Stripe webhook endpoint accepts connected-account payout events in addition to PaymentIntent events. `payout.paid` and terminal `payout.updated` confirm success. `payout.failed`, a canceled terminal payout, or the corresponding terminal update confirm failure. Every terminal event is validated against:

- the stored doFast payout id and user id metadata;
- the stored Stripe payout reference;
- the persisted platform transfer reference;
- exact amount and currency;
- the mapped connected Stripe account from the event context.

On successful bank settlement, `SUBMITTED -> PAID` creates no wallet entry because `PAYOUT_RESERVE` already removed the amount from spendable balance.

On failed/canceled bank settlement, Stripe has returned the failed payout funds to the connected Stripe balance, not to the doFast platform wallet. Therefore doFast first retrieves and validates the original platform transfer and creates an idempotent transfer reversal back to the platform. Only after that reversal succeeds does the existing settlement service apply `SUBMITTED -> FAILED` and the idempotent `PAYOUT_RESTORE`. If transfer recovery is unavailable or ambiguous, webhook processing fails and the wallet reservation stays in place; doFast does not create spendable money while provider money may still be outside the platform.

Disabling the dispatch switch stops new provider dispatch. Signed settlement handling remains available for already-submitted payouts so an operational kill switch cannot strand in-flight financial state.

Production Stripe webhook configuration must deliver connected-account payout events to the same signed `/webhooks/stripe` endpoint. Provider account ids, transfer ids, payout ids and provider failure details remain private financial/operator data and are never exposed in public profiles.

## KYC and account safety

A payout request requires a currently `VERIFIED` identity. The dispatcher rechecks payout eligibility before sending reserved money so a later account suspension or verification revocation cannot silently bypass the safety boundary.

Connected-account creation is also gated on ACTIVE + VERIFIED so an authenticated but unverified account cannot cause doFast to provision external provider resources. The account-link return and refresh URLs are fixed server configuration and validated to HTTPS outside localhost; callers cannot supply an arbitrary redirect target. Docker Compose explicitly forwards these server-side onboarding and dispatch settings instead of relying on host `.env` variables that never reach the API container.

The public profile remains unchanged: it only exposes the existing boolean trust badge. Payout status, payout amounts, provider account IDs, provider references, transfer references and audit events are private financial data.

## Wallet accounting

The wallet ledger and source-of-funds ledger jointly define the payout boundary:

- `PAYOUT_RESERVE` is a negative wallet ledger entry created when the payout request is accepted;
- the reserve may consume only funding lots with `withdrawable=true`;
- current worker earnings (`EARNED_JOB`) are withdrawable;
- Stripe/card funding (`STRIPE_PAYMENT`), migrated historical balance (`LEGACY_UNVERIFIED`) and platform adjustments (`PLATFORM_ADJUSTMENT`) are not withdrawable;
- `PAYOUT_RESTORE` is a positive entry created only when a queued payout is cancelled, a pre-provider dispatch is definitively rejected, or an asynchronous provider failure has first been safely compensated;
- `PAYOUT_RESTORE` restores the exact negative funding movements of the original reserve and therefore cannot change a funding source's withdrawal classification;
- `SUBMITTED` creates no wallet entry because the reservation already removed those funds from spendable balance;
- a successful `PAID` settlement creates no second debit because the money was already removed from available balance at reservation time.

For every wallet, `wallet.balance` must equal the sum of remaining funding lots. A mismatch is a financial-accounting error and fails closed. The application must never infer withdrawability heuristically from transaction history or repair a mismatch by directly changing `wallets.balance`.

This model prevents a Stripe top-up from being converted into cash while still allowing card-funded value to pay for doFast jobs. Ordinary internal spending uses non-withdrawable value before earnings whenever possible, preserving legitimate earned value for later payout.

See `docs/WALLET_SOURCE_OF_FUNDS.md` for the canonical source matrix, restoration rules, legacy migration behavior and operator runbook.

## Administration

`/admin/payouts/**` is protected by the existing admin security boundary. Admins can inspect payout state/events and act only on requests requiring review. A forced failure requires a reason and restores reserved funds through the same idempotent wallet path.

The web operator console at `/admin/payouts` mirrors those backend constraints instead of inventing finance rules in the browser. It supports status filtering, including `SUBMITTED`, paginated payout inspection, provider/failure metadata, immutable event history, audited retry, and audited final rejection with fund restoration. Retry/failure controls are rendered only for `REVIEW_REQUIRED`; a submitted provider operation is display-only because manually retrying it could cause a duplicate payout.

Provider references and internal failure information are not returned by the normal user payout DTO. They are visible only through the admin endpoint and admin-gated UI.

## Database and CI

Flyway `V39__worker_payout_requests.sql` owns payout request/event persistence and wallet payout reservation/restoration. Flyway `V44__stripe_connect_payout_recipients.sql` owns the private user-to-provider account mapping and cached readiness state. Flyway `V45__asynchronous_payout_settlement.sql` adds the `SUBMITTED` state, provider submission timestamp and deduplicated `payout_provider_events` audit table. Flyway `V46__stripe_connect_payout_dispatch.sql` adds the nullable, uniquely indexed provider transfer reference needed for crash-safe Stripe Connect dispatch and failure compensation. Flyway `V49__wallet_source_of_funds.sql` adds funding lots/movements and backfills positive pre-provenance balances as non-withdrawable `LEGACY_UNVERIFIED` value.

`Worker payout smoke` verifies against PostgreSQL and the explicit sandbox provider:

- V44 recipient persistence, V45 async-settlement state, V46 transfer-reference persistence and V49 funding provenance are migrated;
- both Connect kill switches remain disabled in the sandbox smoke and do not affect sandbox cash-out;
- KYC rejection before verification;
- verified payout eligibility;
- the fixture starts from `EARNED_JOB` rather than a synthetic card top-up;
- payout reservation consumes only withdrawable funding;
- idempotent client retry;
- queued cancellation and exact source restoration;
- synchronous sandbox dispatch to `PAID` with immutable audit events and no fake provider submission/transfer references;
- paid balance remains fully covered by the remaining withdrawable earnings lot.

Unit tests additionally enforce funding-source allocation/restoration policy, coverage fail-closed behavior, exact-PaymentIntent refund allocation, `PROCESSING -> SUBMITTED` without wallet mutation, `SUBMITTED -> PAID` without a second debit, `SUBMITTED -> FAILED` with exactly one restore, provider-event replay idempotency, rejection of contradictory terminal events, fresh Stripe Connect readiness before money movement, durable transfer-before-payout ordering, transfer reuse across retries, connected-account event identity validation, and reversal-before-wallet-restore ordering for failed Stripe payouts. Frontend CI continues to run dependency audit, tests, lint and production build.

## Related publish-payment flow

Job publication no longer requires the wallet to cover the full budget. Publication reserves the available wallet amount, requests Stripe payment only for the missing amount, and publishes only after authoritative server-side payment confirmation. The funded amount includes the service reward plus any optional expense budget; expense reimbursement remains separate from the platform fee calculation.

Stripe redirects return to the exact publication id, redirect query secrets are removed from the browser URL immediately, and the frontend treats the backend publication state—not `redirect_status`—as authoritative. Pending publication reservations expire and are released through the idempotent wallet path; attached unfinished PaymentIntents are canceled best-effort only after local cancellation/expiry has committed.

These rules compose with source-of-funds accounting: a cancelled job or publication restores the same lots that funded the original reservation. The user may reuse the returned value inside doFast. Cash-out is available only to the portion whose restored source remains withdrawable, normally worker `EARNED_JOB` value; returning card-funded or legacy value does not make it withdrawable.
