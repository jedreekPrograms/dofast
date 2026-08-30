# Stripe payment disputes and chargeback exposure

## Purpose

A successful Stripe PaymentIntent is not irreversible. A cardholder can later dispute the underlying charge after doFast has already credited wallet funding, locked it in escrow, or released marketplace funds. doFast therefore treats Stripe dispute balance movements as a separate risk lifecycle instead of attempting to mutate the original immutable payment settlement.

This document describes Stripe payment disputes (`charge.dispute.*`). They are distinct from doFast job disputes between a requester and a worker.

## Authoritative Stripe events

The signed Stripe webhook accepts these dispute events:

- `charge.dispute.created`;
- `charge.dispute.updated`;
- `charge.dispute.closed`;
- `charge.dispute.funds_withdrawn`;
- `charge.dispute.funds_reinstated`.

`created`, `updated`, and `closed` update durable provider state only. They do not debit or credit a wallet. Actual doFast financial compensation follows Stripe balance movement events:

- `funds_withdrawn` creates platform exposure and starts wallet recovery;
- `funds_reinstated` clears the exposure and returns only money that doFast had actually recovered from the user's wallet.

Browser state, emails, dispute status strings, and job-dispute actions are never settlement authority for this flow.

### Delivery ordering

Stripe may retry or deliver webhook events out of order. Flyway V53 therefore adds a durable `stripe_state_event_created_at` watermark to every dispute. Provider status, reason and mutable provider metadata advance only from a signed event whose Stripe `Event.created` timestamp is not older than the stored watermark. A delayed older `charge.dispute.updated` can no longer regress a newer `closed`/review state.

Charge identity is validated before the ordering gate, so even a stale signed event cannot silently change the immutable charge binding. Stripe timestamps have second precision; when two events share the same timestamp, an already terminal provider status (`won`, `lost`, `prevented`, `warning_closed`) is not replaced by a different same-second status.

The ordering watermark controls provider metadata only. `funds_withdrawn` and `funds_reinstated` remain separately idempotent financial signals keyed by Stripe event ID, because balance movement must still be applied safely even when its delivery order differs from status events. Historical rows created before V53 have a null watermark; the next valid signed dispute event establishes it without rewriting historical state during migration.

## Identity and validation

Every Stripe dispute must reference a PaymentIntent that already exists in `payment_transactions`. Processing fails closed when:

- the PaymentIntent has not been settled by doFast yet;
- the signed Stripe event does not contain a valid provider creation timestamp;
- the currency is not PLN;
- the disputed amount is non-positive or exceeds the original external settlement;
- a later webhook changes dispute, PaymentIntent, user, amount, currency, or charge identity;
- one PaymentIntent is unexpectedly associated with a second Stripe dispute.

The one-dispute-per-settled-PaymentIntent constraint is deliberately conservative. An unexpected provider shape returns a webhook error for operator investigation rather than risking an over-debit.

## Durable exposure model

Flyway V47 adds `stripe_payment_disputes` and `stripe_payment_dispute_events`; V53 adds the provider-state ordering watermark.

The dispute row stores the provider identities, amount/currency, provider status, provider-state event time, whether funds were withdrawn/reinstated, cumulative wallet recovery, cumulative wallet reinstatement, remaining outstanding exposure, and a recovery sequence. Stripe event IDs are claimed in a separate table so retries are idempotent and conflicting event-ID reuse fails closed.

Wallet balances and `wallet_transactions.balance_after` remain non-negative. Chargebacks never punch a negative number through the existing ledger invariant.

## Recovery policy

When Stripe withdraws disputed funds, doFast immediately recovers:

`min(current free wallet balance, outstanding dispute exposure)`

using an idempotent `CHARGEBACK_RECOVERY` wallet transaction. If the free wallet does not cover the dispute, the remainder stays as durable `outstanding_amount`.

While outstanding exposure exists, the wallet debit guard rejects ordinary outgoing wallet operations, including new escrow/publication reserves and payout reserves. Credits are still allowed. A scheduled recovery worker retries outstanding disputes, so later refunds, earnings, payout restores, or top-ups are collected before they can be spent again.

The recovery worker uses a stable sequence in the operation key (`stripe:dispute:{id}:recovery:{sequence}`), which preserves idempotency across retries and crashes.

## Funds reinstatement

When Stripe sends `charge.dispute.funds_reinstated`, doFast credits only the amount previously recovered from the user's wallet, using one idempotent `CHARGEBACK_REINSTATEMENT` ledger operation. Any portion of the Stripe dispute that doFast never recovered from the user is not credited to the user on reinstatement because it never reduced that user's wallet.

The exposure is then cleared. Delivery order is safe: if a reinstatement event is delivered before the earlier withdrawal event, the aggregate records the reinstatement and a later withdrawal delivery cannot create a new wallet debt.

## Scope boundary

This slice contains provider chargeback exposure but does not implement source-of-funds wallet buckets. If disputed requester funding has already moved through escrow and been paid to a worker, the platform can temporarily carry the outstanding exposure while the requester wallet is restricted and recovered over time. Preventing payout of funds by original funding provenance is a separate wallet-provenance/risk project.

It also does not submit evidence to Stripe or automate dispute representment. Stripe Dashboard/operator evidence workflows remain separate operational work.

## Required validation

Changes to this flow must keep Maven verification, the full container/runtime smoke, and the Payments ledger smoke green. Entity tests explicitly cover state-watermark advancement, stale-event non-regression, stale-event identity validation and same-second terminal-state protection. The ledger smoke sends cryptographically signed Stripe webhook payloads and verifies partial immediate recovery, scheduled recovery of later credits, reinstatement, and replay idempotency against PostgreSQL/Flyway migrations.
