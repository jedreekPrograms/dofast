# Payments and ledger invariants

Carlisle treats wallet balances as a cached projection backed by an append-only ledger. Money mutations are serialized per wallet and every committed mutation must have one stable operation key.

## Wallet rules

- Every wallet balance mutation is executed while holding a pessimistic write lock on that wallet row.
- Debits check available funds while the same lock is held; there is no separate check-then-subtract race.
- Wallet balances can never become negative.
- Money amounts have exactly two decimal places at persistence boundaries.
- Every ledger row stores the signed amount, the resulting `balance_after`, its business type, optional job id and an idempotency `operation_key`.
- Repeating the same operation key with the same wallet/type/amount is a no-op. Reusing it for a different mutation is a conflict.

## Escrow rules

A job has at most one escrow transaction.

- Creating a job locks the requester funds once using `escrow:{jobId}:lock`.
- Completing a job releases the held amount once using `escrow:{jobId}:release`.
- Eligible cancellation or dispute refund returns the held amount once using `escrow:{jobId}:refund`.
- Escrow transitions are pessimistically locked and timestamped.
- `HELD` has no payee and no resolution timestamp.
- `RELEASED` has a payee and a resolution timestamp.
- `REFUNDED` has no payee and has a resolution timestamp.

Job lifecycle and escrow lifecycle are committed in the same database transaction. A failed wallet mutation rolls the job transition back as well.

## Stripe top-ups

Stripe remains the external source of truth for card payment success. doFast credits a wallet only from a webhook with a valid Stripe signature and a `payment_intent.succeeded` event.

Additional invariants:

- Only PLN PaymentIntents are accepted.
- PaymentIntent status must be `succeeded`.
- The signed PaymentIntent metadata supplies the internal user id.
- `payment_transactions.stripe_payment_intent_id` is unique.
- `payment_transactions.stripe_event_id` is unique.
- The webhook first claims the successful PaymentIntent in PostgreSQL using `INSERT ... ON CONFLICT DO NOTHING`, then credits the locked wallet in the same transaction.
- A retry or a second event for the same PaymentIntent is treated as an idempotent no-op only after the persisted user, amount and currency are verified to match. Reusing an event id for another PaymentIntent is a conflict.
- A processing failure rolls back both the claimed payment row and wallet credit, and returns HTTP 500 so Stripe can retry; invalid signatures return HTTP 400.
- Creating a PaymentIntent requires a client `requestId`. It is mapped to a Stripe idempotency key, preventing a network retry from creating a second PaymentIntent for the same top-up attempt.

### Web top-up flow

The wallet page provides the user-facing top-up flow without moving card data through the doFast API:

1. The authenticated web client requests `POST /payments/create-intent` with the selected amount and a unique `requestId`.
2. The API creates an idempotent Stripe PaymentIntent and returns only the client secret required by Stripe.js.
3. The browser loads Stripe.js directly from `https://js.stripe.com` and mounts Stripe Payment Element. Card/payment-method details therefore go directly to Stripe.
4. The browser calls `confirmPayment`. Redirect-based methods return to `/wallet`, where the client retrieves and displays the Stripe payment status.
5. A browser-side `succeeded` result is informational only. It never mutates the wallet. The signed Stripe webhook remains the only top-up credit boundary.
6. After Stripe reports success or processing, the wallet page refreshes balance/history several times to pick up the asynchronous webhook commit.

Configuration:

- `STRIPE_SECRET_KEY` — server-only Stripe secret key. Never expose it to Vite or commit it.
- `STRIPE_WEBHOOK_SECRET` — server-only signing secret for `/webhooks/stripe`.
- `VITE_STRIPE_PUBLISHABLE_KEY` — public Stripe publishable key (`pk_test_...` locally/test, `pk_live_...` only in a controlled production build).

For local end-to-end Stripe testing, configure test keys and forward Stripe test webhooks to `http://localhost:8080/webhooks/stripe`. The signing secret produced for that listener belongs in the local `STRIPE_WEBHOOK_SECRET`; it must not be committed.

## Reconciliation

The administrator reconciliation endpoint is an operational integrity check, not a balance-calculation shortcut.

- Every wallet balance is recomputed from the complete signed ledger and compared with the cached `wallets.balance` value. A non-zero wallet with no ledger entries is therefore a mismatch.
- Every `balance_after` value is checked against the previous ledger row; the first row must equal its own signed amount.
- Non-legacy Stripe payment records are matched against the `TOP_UP` ledger operation keyed as `stripe:intent:{paymentIntentId}`. Wallet, amount, currency and null job linkage must agree.
- A Stripe-style `TOP_UP` ledger entry without its corresponding payment record is also reported as a mismatch.
- Legacy records created before this invariant existed remain visible but are excluded from the strict Stripe-to-ledger pairing check because the old schema did not retain enough linkage data for a safe deterministic backfill.
- Active `HELD` escrow is reported as exposure, not as an integrity failure by itself.

The financial smoke workflow verifies concurrent debit protection, escrow release, refund, ledger sequence, wallet reconstruction, Stripe-to-ledger reconciliation and detection of intentionally corrupted states against a real PostgreSQL/PostGIS instance.

## Production boundary

The repository is safe to exercise with Stripe test keys. Production money movement must not be enabled until real Stripe secrets, webhook endpoint configuration, operational alerting, reconciliation and production deployment controls are configured outside the repository. The web build must receive only the publishable Stripe key; secret and webhook keys remain API-only configuration.
