# Worker payouts

## Purpose

The payout module turns available wallet balance into an auditable cash-out request. It is deliberately separate from job escrow settlement: a completed job first credits the worker wallet (after the snapshotted platform fee), and a later payout request reserves part of that wallet balance for transfer by a payout provider.

Refunded escrow is normal available wallet balance. If an open job is cancelled and its escrow is refunded, the requester may reuse those funds for another job or request a payout subject to the same identity/account/provider rules.

## User flow

`GET /wallet/payouts/eligibility` returns only the current user's payout eligibility, current available balance, minimum amount and provider mode.

`POST /wallet/payouts` requires an idempotent client `requestId`. A successful request:

1. pessimistically locks the user and verifies the account is ACTIVE;
2. requires current identity-verification status `VERIFIED`;
3. requires a configured payout provider;
4. persists the payout request and audit event;
5. debits the wallet using `PAYOUT_RESERVE`, making double-spending impossible while the transfer is pending.

Repeating the same request id and amount returns the same payout without reserving funds again. Reusing the request id for another amount is rejected.

A user may cancel only a still-queued `REQUESTED` payout. Cancellation writes immutable audit events and restores the reserved amount exactly once with `PAYOUT_RESTORE`.

## State machine

```text
REQUESTED -> PROCESSING -> PAID
    |            |
    |            +-> REQUESTED       (retryable provider failure)
    |            +-> REVIEW_REQUIRED (ambiguous result / retry exhaustion)
    |            +-> FAILED          (definitive failure + funds restored)
    |
    +-> CANCELLED (user cancels before processing + funds restored)

REVIEW_REQUIRED -> REQUESTED (audited admin retry)
REVIEW_REQUIRED -> FAILED    (audited admin decision + funds restored)
```

An ambiguous provider result never restores funds automatically. This is intentional: returning the money while the provider might still have sent it could create a double payout. Such cases move to `REVIEW_REQUIRED` for audited operator action.

## Provider boundary and idempotency

`PayoutProvider` receives a stable provider idempotency key derived from the payout id. Retries reuse the same key. A real provider adapter must preserve that contract and map provider outcomes into success, definitive failure, retryable failure or unknown/ambiguous result.

The application defaults to `PAYOUT_PROVIDER=disabled` unless configured. `sandbox` is allowed only when `PAYOUT_SANDBOX_ENABLED=true`; it is for local development and CI and **never sends real money**. The web UI explicitly labels sandbox payouts as test-only.

Before live payouts are enabled, production still needs recipient onboarding and a real provider adapter (for example a regulated marketplace payout product). Provider callbacks must be signature-verified and idempotent.

## KYC and account safety

A payout request requires a currently `VERIFIED` identity. The dispatcher rechecks payout eligibility before sending reserved money so a later account suspension or verification revocation cannot silently bypass the safety boundary.

The public profile remains unchanged: it only exposes the existing boolean trust badge. Payout status, payout amounts, provider references and audit events are private financial data.

## Wallet accounting

The wallet remains the source of truth for spendable balance:

- `PAYOUT_RESERVE` is a negative wallet ledger entry created when the payout request is accepted;
- `PAYOUT_RESTORE` is a positive entry created only when a queued payout is cancelled or a transfer is known to have failed;
- a successful `PAID` payout does not add another debit because the money was already removed from available balance at reservation time.

The current wallet is fungible: it does not artificially mark top-ups, job earnings or escrow refunds as separate spendability buckets. Ledger entries retain provenance, while the user may cash out available balance after KYC. If regulatory/provider requirements later require source-specific withdrawal restrictions, that must be implemented as a dedicated balance-bucket/provenance model rather than inferred heuristically from transaction history.

## Administration

`/admin/payouts/**` is protected by the existing admin security boundary. Admins can inspect payout state/events and act only on requests requiring review. A forced failure requires a reason and restores reserved funds through the same idempotent wallet path.

Provider references and internal failure information are not returned by the normal user payout DTO.

## Database and CI

Flyway `V39__worker_payout_requests.sql` owns payout request/event persistence and extends allowed wallet transaction types for payout reservation/restoration.

`Worker payout smoke` verifies against PostgreSQL and the explicit sandbox provider:

- KYC rejection before verification;
- verified payout eligibility;
- wallet reservation;
- idempotent client retry;
- queued cancellation and exact fund restoration;
- async sandbox dispatch to `PAID` with immutable audit events.

## Related publish-payment flow

Job publication currently requires sufficient wallet balance before escrow can be held. The next payment slice will remove that UX requirement: when a user publishes a job and the wallet covers only part of the budget, doFast will request payment only for the missing amount and automatically continue publication after the provider confirms the payment. The escrow/refund and payout rules above are designed so a later cancelled job can return to wallet and then be reused or cashed out.
