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
```

## Invariants

- A requester cannot accept their own job.
- Only an `OPEN` job can be accepted.
- Only the assigned worker can request completion.
- Escrow is released only after the requester confirms a worker completion request.
- Direct cancellation is allowed only while the job is still `OPEN`.
- Cancelling an `OPEN` job refunds the held amount to the requester.
- Mutation paths use a pessimistic row lock so two workers cannot successfully accept the same job concurrently.
- `@Version` remains enabled as a second line of protection against stale writes.

## API

- `POST /jobs` - create and fund a job.
- `GET /jobs` - list open jobs.
- `GET /jobs/{id}` - fetch job details for an authenticated user.
- `GET /jobs/my` - list jobs created or accepted by the current user.
- `POST /jobs/{id}/accept` - accept an open job (`/take` is kept as a compatibility alias).
- `POST /jobs/{id}/completion` - assigned worker reports completion.
- `POST /jobs/{id}/confirm` - requester confirms completion and releases escrow (`/done` is kept as a compatibility alias).
- `POST /jobs/{id}/cancel` - requester cancels an unaccepted job and receives an escrow refund.

Cancellation after acceptance will be handled by the dispute/cancellation workflow rather than a unilateral refund path.
