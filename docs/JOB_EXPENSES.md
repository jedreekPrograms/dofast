# Job expense budgets and reimbursement

## Commercial model

A job can contain two deliberately separate money components:

- `price` — compensation for the worker's service. The normal escrow and platform-fee rules apply to this amount.
- `expenseBudget` — optional money reserved for purchases or materials needed to perform the job. The platform does not charge the worker-service fee on reimbursed expenses.

For example, a requester can offer PLN 40 for doing groceries and reserve up to PLN 100 for the groceries themselves. Publication funding therefore requires PLN 140, while the platform fee basis remains PLN 40.

`expenseBudget` defaults to PLN 0 for backward compatibility and is capped at PLN 10,000 per job.

## Funding and ledger separation

When a job is created, the service-price escrow is locked through the existing `escrow_transactions` flow. A positive expense budget is locked separately in `job_expense_escrows` and represented in the wallet ledger as `EXPENSE_BUDGET_LOCK`.

Publish-with-payment treats `price + expenseBudget` as the amount that must be available before the job can be published. Existing partial-wallet reservation and Stripe funding behavior therefore covers both components atomically. Once the job is created, the two components are split into their separate escrows again.

Expense settlement uses dedicated wallet transaction types:

- `EXPENSE_BUDGET_LOCK` — requester funds reserved for job costs;
- `EXPENSE_REIMBURSEMENT` — fee-free reimbursement to the assigned worker;
- `EXPENSE_BUDGET_REFUND` — unused or administratively refunded budget returned to the requester.

The labor escrow remains the only source of the normal platform service fee.

## Receipt-backed claims

Submitting an expense claim does **not** immediately transfer money. It records evidence against the held expense budget.

A claim is accepted only when all of the following are true:

1. the job is exactly `IN_PROGRESS`;
2. the caller is the currently assigned worker;
3. the job has a positive, still-held expense escrow;
4. the referenced attachment belongs to the same job, is not deleted, uses `PARTICIPANTS` visibility and was uploaded by that worker;
5. the receipt attachment has not already been used by another claim;
6. the new claim keeps the cumulative claimed amount within the original budget.

Worker receipt uploads are intentionally constrained by the attachment policy: the assigned worker can create only `PARTICIPANTS` attachments and only while the job is `IN_PROGRESS`. Once an attachment is referenced by an expense claim, it becomes retained financial evidence: normal attachment deletion is rejected and the encrypted storage object is preserved. This prevents a worker or requester from removing the receipt after it has entered the reimbursement audit trail.

The web attachment panel mirrors the same backend policy. During `IN_PROGRESS`, the assigned worker sees an upload control that is pinned to `PARTICIPANTS`; viewer-visible and execution-secret visibility options are not offered to the worker. After upload, eligible worker-owned receipt files can be selected directly in the expense form and paired with a PLN amount. The browser enforces positive cent precision and the currently remaining budget for immediate feedback, while the backend remains authoritative and repeats all amount, ownership, status and evidence checks under database locks.

Both job participants can see the participant-only expense summary: original budget, cumulative claims, remaining budget, reimbursed amount, requester refund and immutable claim history. A receipt already referenced by a claim is marked as retained financial evidence and the UI no longer offers a delete action for it. Non-participants never receive the expense summary endpoint data.

Claims are immutable in this first production version. Corrections that would change reimbursement evidence should therefore go through dispute handling rather than silently rewriting history.

## Completion and cancellation

Normal completion settles both escrows in the same database transaction:

- the service-price escrow is released using the existing fee rules;
- the cumulative receipt-backed expense claims are credited to the worker as `EXPENSE_REIMBURSEMENT`;
- the unused part of the expense budget is returned to the requester as `EXPENSE_BUDGET_REFUND`;
- `reimbursedAmount + refundedAmount` must equal the original expense budget.

An unaccepted `OPEN` job can be cancelled normally; its full expense budget is refunded together with the service-price escrow.

For an active job, mutual cancellation is allowed to refund the expense budget only when no expense claim exists. Once the worker has submitted receipt-backed costs, a simple cancellation cannot silently erase them and the parties must use the dispute flow.

## Disputes

Admin dispute resolution is authoritative for the labor escrow and the expense escrow independently. `ResolveDisputeRequest` accepts an optional `approvedExpenseAmount` in PLN. When this field is present, it is the exact portion of already-submitted receipt-backed claims that the administrator accepts as legitimate costs.

- `RELEASE_TO_WORKER` releases the labor escrow. Without an explicit expense amount it preserves the normal completion behavior and reimburses all claimed expenses. With `approvedExpenseAmount`, only that amount is reimbursed and the rest of the original expense budget is refunded to the requester.
- `REFUND_TO_REQUESTER` refunds the labor escrow. Without an explicit expense amount it preserves the legacy full expense-budget refund. With `approvedExpenseAmount`, documented costs can still be reimbursed to the worker even when the service itself is refunded; the remainder of the expense budget returns to the requester.
- `RESUME_JOB` leaves both escrows held and therefore rejects any `approvedExpenseAmount`.

The approved amount cannot be negative, cannot have more than two decimal places and cannot exceed the cumulative amount of submitted claims. These checks run before wallet credits are created. The final expense escrow still obeys the conservation invariant `reimbursedAmount + refundedAmount = budgetAmount`, and the resolution event records the approved expense amount for auditability.

This separation is important commercially: a worker can be denied service compensation while still receiving reimbursement for independently verified materials, or can receive service compensation while only part of disputed receipts are accepted.

## API

`expenseBudget` is accepted on the normal job request and returned on `JobResponse`.

Participant-only expense endpoints:

- `GET /jobs/{jobId}/expenses` — current budget, claimed/reimbursed/refunded totals and immutable claims;
- `POST /jobs/{jobId}/expenses/claims` — assigned worker submits `{ "amount": ..., "attachmentId": ... }` while the job is `IN_PROGRESS`.

Admin dispute resolution accepts `{ "resolution": ..., "note": ..., "approvedExpenseAmount": ... }`; `approvedExpenseAmount` is optional for backward compatibility and must be omitted when resuming a job.

The receipt's storage key, encryption metadata and internal hash are never exposed by the expense API.

## Database invariants

Flyway `V42__job_expense_escrow.sql` adds the job budget, one expense escrow per job and receipt-backed claims. Database checks enforce non-negative bounded amounts, allowed escrow states and terminal conservation of funds. Each attachment can back at most one expense claim.

Application writes additionally use pessimistic job/expense locks and idempotent wallet operation keys. Concurrent claims for the same job therefore serialize against the same held budget, preventing cumulative claims from exceeding it. Claimed receipts are also protected at the attachment-service boundary so soft deletion cannot make financial evidence disappear while a claim still references it.
