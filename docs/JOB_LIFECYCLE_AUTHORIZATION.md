# Job lifecycle authorization

Job lifecycle mutation endpoints treat authorization as part of the database lookup, not as a check performed after loading an arbitrary job by numeric id.

## Visibility boundary

`OPEN` jobs are public marketplace resources. Once a job leaves `OPEN`, an unrelated account must not be able to use lifecycle mutation endpoints as an existence or status oracle. A missing id and a real job outside the caller's authorization scope therefore use the same neutral `Zlecenie nie istnieje` response on those paths.

The public instant-accept path is the deliberate exception to actor ownership: it locks only a job that is still `OPEN` and `INSTANT`. If that scoped lock misses, the service may inspect only the public `OPEN` surface to preserve the useful conflict for an `OPEN + PROPOSALS` job. A terminal or otherwise unavailable id is not loaded through the global pessimistic lookup.

## Mutation ownership

- completion request locks by `job id + assigned worker id` before reading lifecycle state;
- completion confirmation locks by `job id + requester id` before reading lifecycle state or touching escrow;
- direct cancellation locks by `job id + requester id` before reading lifecycle state or issuing refunds;
- instant acceptance locks by `job id + OPEN + INSTANT` before assignment.

The authorized actor still receives lifecycle conflicts after the scoped lock succeeds. This keeps actionable errors for the person who is allowed to operate on the job without exposing private lifecycle state to outsiders.

## Financial and concurrency ordering

The scoped repository methods use pessimistic write locks, so authorization and the state mutation are evaluated against the same locked row. Completion confirmation continues to persist the terminal job state, flush it, clear live tracking, release the main escrow, settle the expense budget, and notify the worker in the existing transaction. Direct cancellation continues to cancel and refund main and expense escrow in the existing transaction. This change does not weaken financial rollback behavior or replace the existing idempotent ledger operations.

Regression tests require outsider requests to fail before the legacy global job lock, persistence, escrow release/refund, or tracking cleanup is reached.
