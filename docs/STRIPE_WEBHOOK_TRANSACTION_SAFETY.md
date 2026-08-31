# Stripe webhook transaction safety

## Scope

doFast treats a signed Stripe webhook as an at-least-once delivery signal. A handler must therefore be safe when the same event is delivered again after a process crash, database failure or HTTP 5xx response.

This document covers the transaction/crash boundary of the Stripe webhook consumers. Provider-operation creation and long-lived dispatch idempotency are documented separately in the payment, refund and payout architecture docs.

## Local-only webhook handlers

The following signed webhook settlement paths perform only local PostgreSQL/ledger writes after validating provider identity:

- wallet top-up `payment_intent.succeeded`;
- job-publication `payment_intent.succeeded`;
- refund state events and wallet reserve restoration;
- dispute/chargeback exposure, recovery and reinstatement.

Their claim/event row, business state and wallet ledger changes participate in the same Spring transaction. A failure before commit must therefore leave no durable half-settlement. Stripe receives HTTP 5xx and may retry the event. Stable provider/event identities and unique operation keys make the successful retry exactly-once from the local ledger perspective.

For top-ups specifically, `PaymentTransactionRepository.claimSuccessfulPayment(...)` claims the PaymentIntent/event before `WalletService.credit(...)`. The claim is not an independent commit. If the later wallet write fails, the enclosing transaction rolls the claim back as well.

## Webhook path with an external provider write

Stripe Connect failed/canceled payout settlement is the exceptional webhook path: it may reverse the previously-created platform Transfer before restoring the local wallet reserve.

That external write cannot participate in the PostgreSQL transaction. Its crash window is protected separately:

1. terminal-state preflight happens before reversal;
2. the reversal uses a stable provider idempotency key;
3. every reversal attempt retrieves and validates the authoritative Transfer first;
4. if a previous reversal succeeded but the process crashed before local commit, retry observes `amount_reversed`/`reversed` and does not reverse twice;
5. a later contradictory `payout.paid` event cannot mark the local payout paid unless the authoritative platform Transfer is still completely unreversed.

Thus a provider-side success followed by a local transaction rollback remains recoverable without inventing local state or moving money twice.

## Real PostgreSQL crash/retry smoke

`.github/scripts/stripe-webhook-transaction-crash-smoke.sh` exercises the local transaction contract through the real HTTP webhook endpoint and PostgreSQL stack.

The smoke:

1. registers a fresh user and confirms a zero wallet;
2. constructs a correctly signed `payment_intent.succeeded` event using the CI webhook secret;
3. installs a temporary PostgreSQL trigger that raises an exception only when the matching Stripe wallet ledger row is inserted;
4. sends the webhook and requires HTTP 500;
5. proves the preceding `payment_transactions` claim, wallet ledger row, wallet balance change and funding lot all rolled back to zero;
6. removes the failure trigger;
7. sends the exact same signed event again and requires one successful payment claim, one wallet ledger credit, one non-withdrawable `STRIPE_PAYMENT` funding lot and the exact wallet balance;
8. sends the same event a third time and proves the duplicate response creates no second claim, credit or funding lot.

The temporary database function/trigger is removed even when the smoke fails so later runtime tests are not contaminated.

## Failure policy

Webhook processing is fail-closed:

- malformed or mismatched provider identity is rejected;
- a local transaction failure returns 5xx rather than acknowledging incomplete settlement;
- no provider event is considered processed merely because an in-transaction claim statement ran;
- external provider writes are never assumed to have failed merely because the local transaction rolled back;
- retries must re-read authoritative provider state where an external write could have succeeded.

## Remaining boundaries

This smoke proves the main local claim-to-ledger rollback boundary on real PostgreSQL and the payout external-write crash boundary is covered by payout-specific tests. Remaining financial launch work includes PaymentIntent creation crash analysis, publication/proposal funding crash analysis, scheduler/restart audit, real Stripe test-mode E2E and production observability/alerting.
