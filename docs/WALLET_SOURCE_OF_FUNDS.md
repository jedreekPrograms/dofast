# Wallet source of funds

## Purpose

The wallet balance is not treated as anonymous cash. doFast persists where every remaining unit of wallet value came from and which earlier debit consumed it. This prevents card-funded value from being converted into cash, allows original-method Stripe refunds to use only the exact original PaymentIntent value, and makes reversals restore the same economic source instead of minting a new generic balance.

Flyway `V49__wallet_source_of_funds.sql` introduces the provenance model.

## Core invariant

For every persisted wallet:

```text
wallet.balance == SUM(wallet_funding_lots.remaining_amount)
```

`WalletFundingSourceService.assertCoverage(...)` enforces that equality at the wallet mutation boundary. A mismatch fails closed with a conflict instead of guessing which source should cover a debit.

Every legitimate positive wallet mutation either creates a new funding origin or explicitly restores earlier negative funding movements. Every legitimate debit allocates negative funding movements to existing lots.

Production code must never mutate `wallets.balance` directly. It must use the wallet service so the wallet transaction, funding lots, funding movements and balance remain one atomic accounting mutation.

## Funding source matrix

| Source | Spend inside doFast | Cash-out / payout | Original-method Stripe refund | Typical origin |
| --- | --- | --- | --- | --- |
| `STRIPE_PAYMENT` | yes | **no** | yes, only for the exact PaymentIntent | successful Stripe wallet/publication funding |
| `EARNED_JOB` | yes | **yes** | no | worker escrow release / expense reimbursement |
| `LEGACY_UNVERIFIED` | yes | **no** | no | V49 migration backfill of pre-provenance balance |
| `PLATFORM_ADJUSTMENT` | yes | **no** | no | explicit non-cash platform adjustment / controlled test seed |

The restriction is attached to the funding lot, not inferred later from transaction history.

## Spend ordering

Normal internal spending may use every covered funding source. To minimize accidental cash-out conversion, ordinary debits consume non-withdrawable value before withdrawable earnings whenever possible.

Example:

```text
10 PLN STRIPE_PAYMENT + 20 PLN EARNED_JOB
internal debit 15 PLN
=> 0 PLN STRIPE_PAYMENT + 15 PLN EARNED_JOB
```

The remaining earned value can still be paid out later.

## Payout policy

`PAYOUT_RESERVE` and legacy `WITHDRAW` are `withdrawable-only` debits. Only lots with `withdrawable=true` are eligible; currently that means `EARNED_JOB`.

A wallet can therefore have enough total balance while still being ineligible for a requested payout:

```text
wallet balance:        105 PLN
Stripe-funded value:  100 PLN
job earnings:            5 PLN
requested payout:       10 PLN
=> rejected
```

This is intentional. A card top-up must not become a cash withdrawal through doFast.

`PAYOUT_RESTORE` never creates a new generic source. Cancellation or definitive provider failure restores the exact funding movements consumed by the original payout reserve, preserving their source type and withdrawability.

## Stripe original-method refunds

A Stripe refund is source-specific rather than a generic wallet debit.

`STRIPE_REFUND_RESERVE` must name the original Stripe PaymentIntent. `WalletFundingSourceService.consumeFromStripePayment(...)` resolves exactly:

```text
(wallet_id, STRIPE_PAYMENT, payment_intent_id)
```

and rejects the refund when that lot no longer has enough remaining value, even if the wallet contains enough money from another PaymentIntent or from job earnings.

Example:

```text
PI_A remaining:  5 PLN
PI_B remaining: 50 PLN
refund PI_A:     7 PLN
=> rejected
```

A failed provider refund is restored with `STRIPE_REFUND_RESTORE` by following the negative funding movement created by the reserve. The value returns to the same PaymentIntent lot.

## Escrow, publication and expense restoration

Operations that release a reservation or refund an internal hold use explicit restoration rather than creating new provenance. This includes:

- `REFUND` for escrow cancellation;
- `ESCROW_ADJUSTMENT_REFUND`;
- `EXPENSE_BUDGET_REFUND`;
- `JOB_PUBLICATION_RELEASE`;
- `PAYOUT_RESTORE`;
- `CHARGEBACK_REINSTATEMENT`;
- `STRIPE_REFUND_RESTORE`.

The restoration service follows source wallet transactions and their negative funding movements, restores only the still-unrestored amount, and records a positive movement whose `restores_movement_id` points at the debit being reversed.

A restoration larger than the unresolved source debit is rejected.

## Job earnings

A worker's released labor escrow is a new economic source and is recorded as `EARNED_JOB` with `withdrawable=true`.

Expense reimbursement is also earned worker value and receives the same withdrawable classification. This is distinct from returning an unused requester expense budget, which restores the requester's original source instead of creating worker earnings.

## Chargebacks

A Stripe dispute may recover wallet value after some of the original card-funded value has already been spent. Chargeback recovery therefore consumes whatever covered wallet lots are actually available at each recovery attempt.

If Stripe later reinstates disputed funds, `CHARGEBACK_REINSTATEMENT` restores the exact lots previously consumed by `CHARGEBACK_RECOVERY`. Mixed recovery remains mixed after reinstatement.

Example:

```text
10 PLN recovered from STRIPE_PAYMENT
15 PLN later recovered from EARNED_JOB
Stripe reinstates dispute funds
=> restore 10 PLN to STRIPE_PAYMENT and 15 PLN to EARNED_JOB
```

The restored 15 PLN remains withdrawable because it was originally earned value; the restored 10 PLN remains non-withdrawable because it was originally card-funded value.

## Legacy migration

V49 backfills every pre-existing positive wallet balance as one `LEGACY_UNVERIFIED` lot.

That value remains spendable inside doFast so migration does not strand existing users, but it is deliberately non-withdrawable because historical data cannot prove that it originated from worker earnings. Operators must not relabel legacy value heuristically.

If a legitimate business process needs to classify historical funds more precisely, it requires an explicit audited migration or operator workflow, not a direct database update.

## Data model

### `wallet_funding_lots`

A lot represents one economic origin and stores:

- wallet;
- source type;
- source reference;
- original amount;
- remaining amount;
- withdrawable flag;
- creation time.

`(wallet_id, source_type, source_reference)` is unique.

### `wallet_funding_movements`

A movement allocates part of a wallet transaction to a funding lot.

- positive movement: origin credit or restoration;
- negative movement: consumption;
- positive restoration movement may reference the exact negative movement through `restores_movement_id`.

This makes source restoration auditable without reconstructing provenance heuristically from wallet transaction order.

## Idempotency and transaction boundary

Wallet transaction `operation_key` remains the money-mutation idempotency key. Provenance is written in the same Spring transaction as the wallet ledger mutation.

An operation replay must therefore resolve to the existing wallet transaction rather than create another funding lot or movement.

Source references serve a different purpose: they identify the economic origin, such as a Stripe PaymentIntent or an earned job release. They are not a replacement for operation idempotency.

## CI fixtures

`.github/scripts/seed-wallet-funding.sh` is a **CI-only** helper for smoke scenarios that need a legitimate starting wallet balance without exercising a provider first. It atomically creates the same four accounting facts expected by V49:

1. wallet balance change;
2. wallet transaction;
3. funding lot;
4. funding movement.

The helper is not a production funding path and must not be exposed through application endpoints or deployment scripts.

Some finance reconciliation smoke tests intentionally perform raw SQL corruption after creating a legitimate baseline. Those writes are documented as deliberate negative fixtures and must remain outside normal application behavior.

## Operational rules

1. Never repair a production mismatch with `UPDATE wallets SET balance=...`.
2. Never mark a Stripe/legacy/platform lot withdrawable merely to unblock a payout.
3. Investigate any coverage mismatch as a financial-accounting incident.
4. Keep payout reservation fail-closed when provider state or source provenance is ambiguous.
5. Keep Stripe refund reservation tied to the exact PaymentIntent.
6. When reversing a reservation, restore the earlier funding movements rather than crediting a new generic lot.
7. Reconciliation and operator tooling may inspect provenance, but must not silently rewrite financial history.

## Verification

Required coverage includes:

- API Maven tests for funding allocation/restoration policy;
- container/runtime smoke for normal escrow and cancellation;
- payments-ledger smoke for coverage, Stripe refunds and chargebacks;
- payout smoke for withdrawable-only reservation and exact restoration;
- platform-fee smoke for `EARNED_JOB` worker credit;
- publication payment smoke for reservation/release provenance;
- Flyway migration on PostgreSQL/PostGIS.

A source-of-funds change is not production-ready until all exact-head financial and runtime checks are green.
