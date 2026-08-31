# Stripe original-method refunds

## Scope

doFast can return settled Stripe-funded money to the payment method that originally funded a wallet top-up or job publication. The API supports full remaining refunds and partial refunds. Stripe, not the browser, is the authority for provider state.

Endpoints:

- `POST /payments/refunds` creates an idempotent refund request.
- `GET /payments/refunds/{id}` returns the authenticated owner's current local refund state.

A client `requestId` is unique per user. Reusing it with different payment or amount data is rejected.

## Solvency boundary

A refund is never created at Stripe first. doFast first locks the original `PaymentTransaction`, verifies ownership/currency/purpose/refund capacity, persists the refund request, and debits the requested amount from free wallet balance as `STRIPE_REFUND_RESERVE`.

If those free funds are no longer available, the refund request fails before any provider call. Escrow, payout reservations and other already-consumed money are not silently made negative to fund a customer refund.

This is deliberately a solvency guard, not source-of-funds provenance. Until wallet provenance buckets are shipped, another free wallet credit can economically cover a refund of an older Stripe payment. Provenance/withdrawal policy remains a separate hardening item.

## Dispute interaction

A PaymentIntent that has ever entered the local Stripe dispute ledger is not eligible for a new refund. This avoids intentionally issuing a second reimbursement while a payment is or was subject to chargeback handling.

The dispatch path rechecks dispute state immediately before the provider call. If a dispute appears before the first provider attempt, the local reservation is canceled and restored. If a dispute appears after an ambiguous provider attempt, the request goes to `REVIEW_REQUIRED` and the reservation stays held because Stripe may already have accepted the refund.

There is still an unavoidable network race between the final local check and Stripe accepting the provider request; signed Stripe events and operator review are the reconciliation authority for that edge.

## Provider dispatch and retries

Refund creation uses Stripe's Refund API with the original `payment_intent`, optional partial `amount`, `requested_by_customer`, doFast metadata, and idempotency key `dofast:refund:{localRefundId}`.

Provider timeouts are treated as ambiguous. doFast does not restore the wallet reservation merely because a network call failed. The same provider request is retried with the same Stripe idempotency key.

Stripe API v1 can prune idempotency keys once they are at least 24 hours old. Reusing a pruned key can execute a new request instead of replaying the original result. That matters for partial refunds: an old ambiguous retry must never depend on the provider remembering a key forever.

For this reason stale `DISPATCHING` rows are recovered under a bounded safety window:

- an ambiguous dispatch younger than 23 hours may be requeued with exactly the same provider idempotency key;
- an ambiguous dispatch at least 23 hours old is moved to `REVIEW_REQUIRED` with `provider_idempotency_window_expired`;
- the local `STRIPE_REFUND_RESERVE` remains held in review and is not restored automatically;
- the scheduler never issues another provider `POST` for that expired ambiguous operation.

The one-hour margin is intentional. It keeps automatic retries inside the documented Stripe v1 24-hour idempotency guarantee rather than attempting a request at the provider boundary. Stale recovery selects rows with `FOR UPDATE SKIP LOCKED`, so multiple API instances cannot simultaneously recover the same ambiguous refund.

Repeated ordinary provider failures still reach `REVIEW_REQUIRED` after the configured local attempt limit rather than creating money twice.

## Signed settlement

The existing signed `/webhooks/stripe` endpoint processes managed:

- `refund.created`
- `refund.updated`
- `refund.failed`

Events are deduplicated by Stripe event id. Each managed refund is validated against its local PaymentIntent, amount, currency and doFast metadata. `event.created` ordering prevents an older event from regressing newer state.

Refund statuses are persisted as `PENDING`, `REQUIRES_ACTION`, `SUCCEEDED`, `FAILED`, or `CANCELED`. A genuinely newer provider failure can supersede an earlier success observation because Stripe can report failed refunds asynchronously.

Stripe event timestamps have second-level precision, so two different webhook events can share the same `event.created`. If two same-second events conflict after a provider-resolved state has already been observed, doFast no longer lets delivery order choose the financial outcome. The refund moves to `REVIEW_REQUIRED`, keeps the wallet reservation untouched, preserves the last unambiguous provider observation, and waits for a strictly newer signed provider event (or operator reconciliation) to resolve the state. This avoids both premature wallet restoration and silent local state regression.

When Stripe definitively reports `FAILED` or `CANCELED`, the held amount is credited back exactly once as `STRIPE_REFUND_RESTORE`. A successful refund never returns the reserved money to the wallet.

Unmanaged Stripe refunds are ignored by this settlement path. Operators must not issue refunds directly in the Stripe Dashboard for doFast-managed payments because doing so bypasses the wallet reservation and local refund lifecycle.

## Operational validation

Changes to this flow must keep Maven/API verification, web verify, full container/runtime smoke, payment-ledger smoke, publication payment smoke, platform-fee smoke and payout smoke green. The payment-ledger workflow includes signed-webhook PostgreSQL scenarios for successful refund settlement, failed-refund restoration and webhook replay idempotency, plus a real stale-dispatch recovery scenario proving that an ambiguity older than the safe provider idempotency window goes to review without another provider attempt or wallet restore.
