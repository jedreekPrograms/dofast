# Platform fees and settlement

## Product policy

The default doFast platform fee is configured as **100 basis points (1.00%)** of the final gross job amount. The value is deliberately low and transparent, but product copy must not claim that it is the lowest fee on the market unless that claim has been independently verified at the time it is shown.

The requester funds only the agreed gross job price. This slice does not add a separate requester surcharge. On a successful worker settlement:

`gross escrow = platform fee + worker payout`

For example, at 1.00%:

- gross job amount: 40.00 PLN,
- platform fee: 0.40 PLN,
- worker payout: 39.60 PLN.

Money is rounded to two decimal places with `HALF_UP` when the fee is calculated.

## Fee snapshot

The configured fee rate is copied into `escrow_transactions.platform_fee_basis_points` when escrow is first created. It is therefore part of the commercial terms of that job.

Changing `PLATFORM_FEE_BASIS_POINTS` later does **not** change already-published jobs. Proposal-based price changes update the gross escrow amount, but retain the original fee-rate snapshot. Quote UI for an existing job reads that snapshot rather than the current global configuration.

Migration V38 assigns `0` basis points to historical escrow rows. Existing completed or active jobs are therefore never charged retroactively.

## Settlement lifecycle

A fee is recognized only when escrow transitions from `HELD` to `RELEASED`:

1. the final gross escrow amount is locked and verified,
2. the snapshotted fee is calculated,
3. the worker wallet is credited with the net payout using the existing idempotent `ESCROW_RELEASE` operation,
4. the fee is written to the separate `platform_revenue_entries` ledger,
5. the escrow transaction stores both `platform_fee_amount` and `payee_amount`,
6. database constraints enforce that fee + worker payout exactly equals gross escrow.

The dispute resolution `RELEASE_TO_WORKER` uses the same `TransactionService.releaseMoney(...)` path, so ordinary completion and admin dispute settlement cannot diverge financially.

## Refunds and resumed jobs

No platform fee is charged when escrow is refunded to the requester. `REFUNDED` rows keep `platform_fee_amount` and `payee_amount` null, and no platform revenue entry is created.

Resuming a disputed job leaves escrow held and therefore recognizes no fee until a later successful release.

## Revenue ledger

`platform_revenue_entries` is separate from user wallets. A platform fee is not represented as a hidden user-wallet mutation. Each revenue entry has:

- the escrow transaction,
- the job,
- revenue type (`PLATFORM_FEE`),
- exact amount,
- an idempotent operation key,
- creation timestamp.

The operation key is `platform-fee:job:{jobId}:release`. A uniqueness constraint prevents duplicate fee recognition.

## Reconciliation

Admin finance reconciliation now checks the platform revenue ledger as another accounting invariant. A healthy report requires:

- no wallet balance mismatches,
- no wallet ledger sequence mismatches,
- no Stripe ledger mismatches,
- no platform revenue/escrow settlement mismatches.

It also exposes the accumulated platform-fee revenue amount for operational accounting.

## API transparency

Authenticated clients can use:

- `GET /payments/platform-fee-policy` for the current policy used by newly-created escrow,
- `GET /payments/platform-fee-quote?amount=...` for a new-job quote,
- `GET /payments/platform-fee-quote?amount=...&jobId=...` for an existing job. The latter uses that job's snapshotted fee rate and applies normal job-detail visibility/blocking policy.

The web app uses these endpoints to show gross, fee and expected worker payout before publication and before a worker submits a proposal.

## Configuration

`PLATFORM_FEE_BASIS_POINTS=100` is the default. The backend accepts values from 0 to 1000 basis points (0% to 10%). Production changes should be treated as commercial configuration changes and rolled out deliberately; they affect only newly-created escrow.

The legacy Payments ledger smoke explicitly runs with `PLATFORM_FEE_BASIS_POINTS=0` to isolate wallet concurrency/idempotency assumptions. A separate Platform fee settlement smoke runs with 100 basis points and verifies the real gross/fee/net accounting path, refunds and reconciliation.

## Future finance modules

This ledger is intentionally separate from future worker payout rails, tax invoices and VAT/accounting documents. A later Stripe Connect or bank-payout module should settle from the worker's available balance without rewriting the historical job gross/fee/net breakdown.
