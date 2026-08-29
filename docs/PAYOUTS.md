# Worker payouts

## Purpose

The payout module turns available wallet balance into an auditable cash-out request. It is deliberately separate from job escrow settlement: a completed job first credits the worker wallet (after the snapshotted platform fee), and a later payout request reserves part of that wallet balance for transfer by a payout provider.

Refunded escrow is normal available wallet balance. If an open job is cancelled and its escrow is refunded, the requester may reuse those funds for another job or request a payout subject to the same identity/account/provider rules.

## User flow

`GET /wallet/payouts/eligibility` returns the current user's payout eligibility, balance, minimum amount, provider mode, and connected-recipient readiness. Stripe Connect setup readiness is reported independently of whether live payout dispatch is enabled.

`GET /wallet/payouts/onboarding/status` reads the last authoritative connected-account state cached by doFast. `POST /wallet/payouts/onboarding/refresh` re-reads provider state. `POST /wallet/payouts/onboarding/link` creates or reuses the user's mapped Express account and returns a single-use Stripe-hosted onboarding link. Provisioning is available only to ACTIVE users whose doFast identity verification is currently `VERIFIED`. Account creation is serialized by a fresh pessimistic user lock so concurrent onboarding requests cannot race past the local one-account-per-provider invariant.

Stripe `refresh_url` returns to the wallet with `stripe-connect=refresh`; the web app immediately requests a fresh single-use Account Link instead of trying to reuse an expired URL. The normal return path refreshes provider state before presenting readiness.

`POST /wallet/payouts` requires an idempotent client `requestId`. A successful request:

1. pessimistically locks the user and verifies the account is ACTIVE;
2. requires current identity-verification status `VERIFIED`;
3. requires a configured payout provider;
4. if the configured provider is Stripe Connect, requires the Connect kill switch and refreshes the authoritative Stripe account state immediately before any wallet reservation;
5. persists the payout request and audit event;
6. debits the wallet using `PAYOUT_RESERVE`, making double-spending impossible while the transfer is pending.

Repeating the same request id and amount returns the same payout without reserving funds again. Reusing the request id for another amount is rejected.

A user may cancel only a still-queued `REQUESTED` payout. Cancellation writes immutable audit events and restores the reserved amount exactly once with `PAYOUT_RESTORE`.

## State machine

```text
REQUESTED -> PROCESSING -> PAID          (provider confirms terminal success synchronously)
    |            |
    |            +-> SUBMITTED -> PAID   (provider accepted; later settlement confirms success)
    |            |       |
    |            |       +-------> FAILED + PAYOUT_RESTORE (later definitive failure)
    |            |
    |            +-> REQUESTED       (retryable provider failure before acceptance)
    |            +-> REVIEW_REQUIRED (ambiguous result / retry exhaustion before acceptance)
    |            +-> FAILED          (definitive pre-acceptance failure + funds restored)
    |
    +-> CANCELLED (user cancels before processing + funds restored)

REVIEW_REQUIRED -> REQUESTED (audited admin retry)
REVIEW_REQUIRED -> FAILED    (audited admin decision + funds restored)
```

`SUBMITTED` is intentionally non-terminal. It means a provider has accepted the payout operation and returned a durable reference, but doFast has not yet received authoritative terminal settlement. A submitted payout is never automatically retried or restored merely because settlement is slow. This prevents a second transfer from being created while the first might still reach the recipient.

An ambiguous pre-acceptance provider result never restores funds automatically. Returning the money while the provider might still have sent it could create a double payout, so such cases move to `REVIEW_REQUIRED` for audited operator action.

## Provider boundary and idempotency

`PayoutProvider` receives a stable provider idempotency key derived from the payout id. Retries reuse the same key. Synchronous providers can return terminal success as before. Asynchronous providers return a durable reference with `PayoutDispatchResult.submitted(...)`; the request moves to `SUBMITTED` and retains its original wallet reservation until a terminal provider settlement is applied.

`PayoutProviderSettlementService` is the provider-neutral settlement boundary. It locks the payout by the unique `(provider_code, provider_reference)` pair and deduplicates provider notifications by `(provider_code, provider_event_id)`. A `PAID` settlement finalizes without another wallet debit. A definitive `FAILED` settlement restores the reserved amount through the existing idempotent `PAYOUT_RESTORE` operation. Replayed events cannot mutate money twice, and a terminal event contradicting an already terminal state is rejected rather than silently rewriting financial history.

The application defaults to `PAYOUT_PROVIDER=disabled` unless configured. `sandbox` is allowed only when `PAYOUT_SANDBOX_ENABLED=true`; it is for local development and CI and **never sends real money**. The sandbox provider remains synchronous so existing smoke coverage still ends directly in `PAID`.

Stripe Connect recipient onboarding has a separate kill switch, `PAYOUT_STRIPE_CONNECT_ENABLED`. It creates a persistent provider-account mapping but does not itself enable money movement. This slice still does **not** register `stripe-connect` as a `PayoutProvider`; setting `PAYOUT_PROVIDER=stripe-connect` therefore remains fail-closed until the provider-specific transfer/payout adapter, signed webhook ingestion and reconciliation are shipped and reviewed.

Stripe distinguishes moving platform funds to a connected account from the connected account's bank payout. Real Connect payout activity is asynchronous and must be tracked with provider events such as `payout.paid` and `payout.failed`; therefore a successful submission must not be mapped directly to doFast `PAID`.

Connected-account readiness requires all of the following provider-confirmed conditions: account details submitted, payouts enabled, the `transfers` capability active, and no currently-due or past-due requirements. A transient Stripe API error does not overwrite the last successful readiness snapshot, and a future live request must obtain a fresh successful provider read before reserving wallet funds.

## KYC and account safety

A payout request requires a currently `VERIFIED` identity. The dispatcher rechecks payout eligibility before sending reserved money so a later account suspension or verification revocation cannot silently bypass the safety boundary.

Connected-account creation is also gated on ACTIVE + VERIFIED so an authenticated but unverified account cannot cause doFast to provision external provider resources. The account-link return and refresh URLs are fixed server configuration and validated to HTTPS outside localhost; callers cannot supply an arbitrary redirect target. Docker Compose explicitly forwards these server-side onboarding settings instead of relying on host `.env` variables that never reach the API container.

The public profile remains unchanged: it only exposes the existing boolean trust badge. Payout status, payout amounts, provider account IDs, provider references and audit events are private financial data.

## Wallet accounting

The wallet remains the source of truth for spendable balance:

- `PAYOUT_RESERVE` is a negative wallet ledger entry created when the payout request is accepted;
- `PAYOUT_RESTORE` is a positive entry created only when a queued payout is cancelled or a transfer is known to have failed;
- `SUBMITTED` creates no wallet entry because the reservation already removed those funds from spendable balance;
- a successful `PAID` settlement creates no second debit because the money was already removed from available balance at reservation time.

The current wallet is fungible: it does not artificially mark top-ups, job earnings or escrow refunds as separate spendability buckets. Ledger entries retain provenance, while the user may cash out available balance after KYC. If regulatory/provider requirements later require source-specific withdrawal restrictions, that must be implemented as a dedicated balance-bucket/provenance model rather than inferred heuristically from transaction history.

## Administration

`/admin/payouts/**` is protected by the existing admin security boundary. Admins can inspect payout state/events and act only on requests requiring review. A forced failure requires a reason and restores reserved funds through the same idempotent wallet path.

The web operator console at `/admin/payouts` mirrors those backend constraints instead of inventing finance rules in the browser. It supports status filtering, including `SUBMITTED`, paginated payout inspection, provider/failure metadata, immutable event history, audited retry, and audited final rejection with fund restoration. Retry/failure controls are rendered only for `REVIEW_REQUIRED`; a submitted provider operation is display-only because manually retrying it could cause a duplicate payout.

Provider references and internal failure information are not returned by the normal user payout DTO. They are visible only through the admin endpoint and admin-gated UI.

## Database and CI

Flyway `V39__worker_payout_requests.sql` owns payout request/event persistence and wallet payout reservation/restoration. Flyway `V44__stripe_connect_payout_recipients.sql` owns the private user-to-provider account mapping and cached readiness state. Flyway `V45__asynchronous_payout_settlement.sql` adds the `SUBMITTED` state, provider submission timestamp and the deduplicated `payout_provider_events` audit table.

`Worker payout smoke` verifies against PostgreSQL and the explicit sandbox provider:

- V44 recipient persistence is migrated even when Connect is disabled;
- V45 async-settlement schema is present;
- disabled Connect setup remains fail-closed and does not affect sandbox cash-out;
- KYC rejection before verification;
- verified payout eligibility;
- wallet reservation;
- idempotent client retry;
- queued cancellation and exact fund restoration;
- synchronous sandbox dispatch to `PAID` with immutable audit events and no fake `provider_submitted_at`.

Unit tests additionally enforce `PROCESSING -> SUBMITTED` without wallet mutation, `SUBMITTED -> PAID` without a second debit, `SUBMITTED -> FAILED` with exactly one restore, provider-event replay idempotency, rejection of contradictory terminal events, fresh Stripe Connect readiness before reservation, and the onboarding/account safety gates. Frontend CI runs dependency audit, lint and production build.

## Related publish-payment flow

Job publication no longer requires the wallet to cover the full budget. Publication reserves the available wallet amount, requests Stripe payment only for the missing amount, and publishes only after authoritative server-side payment confirmation. The funded amount includes the service reward plus any optional expense budget; expense reimbursement remains separate from the platform fee calculation.

Stripe redirects return to the exact publication id, redirect query secrets are removed from the browser URL immediately, and the frontend treats the backend publication state—not `redirect_status`—as authoritative. Pending publication reservations expire and are released through the idempotent wallet path.

These rules intentionally compose with payouts: a later cancelled job can return funds to the wallet, after which the user may reuse or cash them out subject to KYC, account and payout-provider eligibility.
