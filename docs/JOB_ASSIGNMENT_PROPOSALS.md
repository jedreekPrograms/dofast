# Job assignment modes and worker proposals

## Product model

doFast supports two distinct ways to choose a worker. The goal is to keep simple errands fast while also supporting jobs where the requester wants to choose a specific person.

### `INSTANT`

`INSTANT` is the default and preserves the original doFast flow. An eligible user may immediately accept an `OPEN` job and become the assigned worker.

This mode is appropriate for low-friction errands such as:

- picking up groceries from a nearby shop;
- delivering a small package;
- collecting a prepared order;
- other tasks where the requester is comfortable with the first eligible person taking the job.

Old clients that do not send `assignmentMode` continue to create `INSTANT` jobs.

### `PROPOSALS`

For work where the requester wants to choose the worker, the job may use `PROPOSALS`. Direct `/accept` is forbidden for these jobs. Instead, authenticated workers submit a private proposal and the requester explicitly accepts one.

Proposal mode does not imply price negotiation. A requester may use proposals only to compare profiles/messages while keeping the published price fixed.

## Requester-controlled price negotiation

`priceNegotiationEnabled` is independent from the decision to use proposals:

- `INSTANT + false` — immediate take at the published price;
- `PROPOSALS + false` — requester chooses a worker, but every proposal uses the published price;
- `PROPOSALS + true` — workers may propose another positive amount;
- `INSTANT + true` — invalid and rejected by the application/database model.

This deliberately avoids forcing a Fixly-style bidding flow onto simple errands.

## Proposal API

Authenticated endpoints:

- `POST /jobs/{jobId}/proposals` — submit one proposal for the caller;
- `GET /jobs/{jobId}/proposals` — requester sees all proposals; another user sees only their own proposal;
- `DELETE /jobs/{jobId}/proposals/{proposalId}` — proposer withdraws their still-active proposal;
- `POST /jobs/{jobId}/proposals/{proposalId}/accept` — requester accepts one submitted proposal.

V1 allows one proposal row per worker/job. Withdrawal preserves the historical row instead of deleting it.

Competitors never receive other workers' proposal prices or messages. The proposal response exposes public user IDs only; email, exact location, verification documents, moderation data, wallet data and payment details are not part of this API.

## Escrow semantics

The published job price is still fully held in escrow when the job is created. This protects the worker from a requester selecting someone before having any funded budget.

When a proposal is accepted, escrow is atomically reconciled to the accepted amount before the worker is assigned:

- same amount — no wallet mutation is required;
- higher amount — only the positive delta is additionally debited and locked;
- lower amount — only the excess held amount is credited back to the requester;
- insufficient balance for a higher proposal — acceptance fails and the transaction rolls back, leaving the job `OPEN` and the proposal unaccepted.

Adjustment ledger rows use dedicated types:

- `ESCROW_ADJUSTMENT_LOCK`;
- `ESCROW_ADJUSTMENT_REFUND`.

Each adjustment has an idempotent operation key scoped by job and proposal. The final escrow transaction amount becomes the accepted job price, so later release/refund continues to operate on one authoritative amount.

## Concurrency and lifecycle

Proposal submission, withdrawal and acceptance lock the job row before mutating proposal state. This serializes proposal writes with final worker selection and prevents a proposal from being submitted after the job has concurrently moved out of `OPEN`.

On successful acceptance:

1. the final escrow amount is reconciled;
2. the job price becomes the accepted proposal amount;
3. the chosen worker is assigned and the job enters the normal `IN_PROGRESS` lifecycle;
4. the chosen proposal becomes `ACCEPTED`;
5. other still-submitted proposals become `REJECTED`;
6. point-to-point jobs initialize the existing live-tracking flow;
7. the chosen worker receives a persistent proposal-accepted notification.

All existing completion, cancellation-agreement, dispute, tracking and escrow-release rules continue from the same job lifecycle after assignment.

## Blocking and privacy

Bilateral user blocking is enforced when a proposal is submitted and checked again when the requester accepts it. A previously submitted proposal therefore cannot be used to start a new commercial interaction after either side blocks the other.

Historical proposal visibility is intentionally retained for accountability. Blocking does not delete transaction history.

Proposal mode does not grant exact-location access. Before assignment, workers still see only the same public job-location fields available through normal discovery. Exact addresses, routes and live tracking remain behind the existing participant/lifecycle authorization boundary.

## Schema

Flyway `V35__job_assignment_proposals.sql`:

- adds `assignment_mode` and `price_negotiation_enabled` to `jobs` with backward-compatible defaults and database checks;
- creates `job_proposals` with optimistic versioning, one proposal per job/proposer, positive amount validation and indexed job/proposer lookups.

## Web experience

The create-job page exposes the product choice in user-facing language rather than backend enum names:

- **Kto pierwszy, ten bierze** maps to `INSTANT`;
- **Chcę wybrać wykonawcę** maps to `PROPOSALS`;
- price negotiation appears as a separate checkbox only in proposal mode.

The published amount is explicitly described as the amount initially held in escrow. The UI explains that accepting a higher proposal requires the missing delta to be funded first and accepting a lower proposal returns the excess.

Discovery cards respect the assignment mode. Proposal jobs never render the legacy direct-accept action; they link to the private proposal flow instead and state whether price negotiation is enabled.

On job details:

- an eligible worker sees a form for only their own proposal;
- fixed-price proposal jobs do not expose a price input;
- negotiable jobs prefill the published budget but allow another positive amount;
- a worker can see and withdraw only their own still-submitted proposal;
- the requester sees all candidates with their public trust profiles, proposed amount and optional private message;
- accepting a proposal updates the job immediately to the final price and normal active lifecycle;
- remaining submitted proposals are shown as rejected after another worker is selected.

The frontend never attempts to infer or display competitors' offers for workers. It relies on the server-side visibility contract of `GET /jobs/{jobId}/proposals`, so custom clients and normal UI follow the same privacy boundary.
