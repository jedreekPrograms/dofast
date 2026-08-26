# Job lifecycle

The job lifecycle is the core marketplace state machine. State transitions are performed by authenticated actors and serialized with a database row lock for mutation paths.

## States

```text
OPEN
  | accept (worker)
  v
IN_PROGRESS
  | request completion (assigned worker)
  v
COMPLETION_REQUESTED
  | confirm completion (requester)
  v
DONE

OPEN -- cancel (requester) --> CANCELLED

IN_PROGRESS
  | cancellation request (requester or worker)
  | counterparty approval
  v
CANCELLED
```

A cancellation request is a separate aggregate and does not change the job status while it is waiting for the other participant. The job remains `IN_PROGRESS` until the counterparty approves. Declining or withdrawing the request leaves the job active.

## Invariants

- A requester cannot accept their own job.
- Only an `OPEN` job can be accepted.
- Only the assigned worker can request completion.
- Escrow is released only after the requester confirms a worker completion request.
- Direct cancellation is allowed only while the job is still `OPEN`.
- Cancelling an `OPEN` job refunds the held amount to the requester.
- After acceptance, cancellation is negotiated between the two participants rather than performed unilaterally.
- Negotiated cancellation is available only while the job is `IN_PROGRESS`; once completion is requested, completion confirmation or the dispute workflow must resolve the job.
- Only one `PENDING` cancellation request may exist for a job at a time.
- The participant who created a cancellation request cannot approve or decline their own request; they may only withdraw it.
- Counterparty approval atomically moves the job to `CANCELLED`, stops live location sharing and refunds the held escrow to the requester.
- Mutation paths use a pessimistic row lock so two workers cannot successfully accept the same job concurrently and cancellation decisions cannot race against lifecycle changes.
- `@Version` remains enabled as a second line of protection against stale writes.
- Flyway constraints enforce valid cancellation-request states and resolution metadata even if a write bypasses the service layer.

## API

- `POST /jobs` - create and fund a job.
- `GET /jobs` - list open jobs.
- `GET /jobs/{id}` - fetch job details for an authenticated user.
- `GET /jobs/my` - list jobs created or accepted by the current user.
- `POST /jobs/{id}/accept` - accept an open job (`/take` is kept as a compatibility alias).
- `POST /jobs/{id}/completion` - assigned worker reports completion.
- `POST /jobs/{id}/confirm` - requester confirms completion and releases escrow (`/done` is kept as a compatibility alias).
- `POST /jobs/{id}/cancel` - requester cancels an unaccepted job and receives an escrow refund.
- `GET /jobs/{id}/cancellation` - return the pending negotiated cancellation request for a participant, or `204` when none exists.
- `POST /jobs/{id}/cancellation` - create a negotiated cancellation request with a reason.
- `POST /jobs/{id}/cancellation/approve` - counterparty approves; job is cancelled, tracking stops and escrow is refunded.
- `POST /jobs/{id}/cancellation/decline` - counterparty rejects the request and the job remains active.
- `POST /jobs/{id}/cancellation/withdraw` - requester withdraws their own pending cancellation request.

## Live tracking interaction

The database keeps a terminal-state trigger as defense in depth so exact courier location is cleared when a job is paused or closed. Application lifecycle paths also stop the tracking service so connected clients receive the stopped state. The terminal status transition is flushed before application-side cleanup to keep Hibernate optimistic locking synchronized with the trigger.
