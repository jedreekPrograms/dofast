# Carlisle disputes and moderation

## Purpose

The dispute module is the administrative safety boundary around accepted jobs and held escrow funds. A dispute is not only a support ticket: opening and resolving it changes the job lifecycle and the escrow transaction atomically.

## Core invariants

1. Only the requester or assigned worker can open a dispute.
2. A dispute can be opened only while a job is `IN_PROGRESS` or `COMPLETION_REQUESTED`.
3. The escrow transaction must still be `HELD` when a dispute is opened.
4. Opening a dispute moves the job to `DISPUTED` without moving money.
5. A job may have historical disputes, but at most one `OPEN`/`UNDER_REVIEW` dispute at a time. PostgreSQL enforces this with a partial unique index.
6. Normal job completion/cancellation cannot bypass a dispute because their lifecycle guards reject `DISPUTED` jobs.
7. Escrow transitions use a pessimistic database lock before release/refund.
8. Only admins can use `/admin/disputes/**`; service-layer role checks provide defense in depth.
9. A claimed active dispute cannot be resolved by another admin.
10. Every open, claim, resolution and user cancellation is recorded in `dispute_events`.

## User flow

- `POST /disputes` — open a dispute for an active accepted job.
- `GET /disputes/my` — list all disputes involving the authenticated user as requester or worker.
- `GET /disputes/{id}` — read a dispute and its audit trail if the caller is a participant (or admin).
- `POST /disputes/{id}/cancel` — opener may cancel only while status is `OPEN`; the job returns to the status saved before the dispute and escrow remains held.

The web application exposes this flow under **Spory** and links directly from eligible items in **Moje zlecenia**.

## Admin flow

- `GET /admin/disputes` — paginated queue, optional `status` filter, oldest cases first.
- `GET /admin/disputes/{id}` — case detail plus audit events.
- `POST /admin/disputes/{id}/claim` — assign the case to the authenticated admin and move it to `UNDER_REVIEW`.
- `POST /admin/disputes/{id}/resolve` — perform one of the supported decisions with a mandatory written justification.

### Resolution: `RELEASE_TO_WORKER`

- lock the escrow row,
- transfer the held amount to the assigned worker,
- set escrow to `RELEASED`,
- set the job to `DONE`,
- set the dispute to `RESOLVED` and append an audit event.

### Resolution: `REFUND_TO_REQUESTER`

- lock the escrow row,
- return the held amount to the requester,
- set escrow to `REFUNDED`,
- set the job to `CANCELLED`,
- set the dispute to `RESOLVED` and append an audit event.

### Resolution: `RESUME_JOB`

- verify escrow is still `HELD`,
- restore the job to the exact pre-dispute state (`IN_PROGRESS` or `COMPLETION_REQUESTED`),
- do not move money,
- resolve the current dispute while preserving its history.

A later independent problem may therefore produce a new dispute for the same job; only one active case is permitted at any given time.

## Privacy

Exact job coordinates/private location remain restricted to the requester and the assigned worker. The worker keeps access while the job is `DISPUTED`, because the location can be necessary to understand/evidence an active case. Public discovery and nearby APIs still never expose exact coordinates or the private location label.

## Concurrency

Dispute mutation uses optimistic versioning on the dispute aggregate plus pessimistic row locks for mutations. Escrow release/refund locks the corresponding escrow transaction with `PESSIMISTIC_WRITE`. This prevents two concurrent administrative/user actions from paying or refunding the same held funds twice.

## Verification

CI verifies the full runtime path against a real PostGIS/PostgreSQL container:

- requester + worker registration/login,
- funded requester wallet,
- job creation and escrow hold,
- worker acceptance,
- participant-only exact location,
- dispute opening and `DISPUTED` lifecycle state,
- escrow remaining `HELD`,
- blocked normal completion during a dispute,
- `403` for a normal user attempting admin dispute APIs,
- admin queue + claim,
- admin refund resolution,
- final `CANCELLED` job,
- final `REFUNDED` escrow,
- requester wallet restored to its pre-job balance,
- persisted dispute audit events.
