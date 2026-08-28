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
- `GET /jobs/{jobId}/proposals/{proposalId}/acceptance-funding` — requester-only funding preflight for accepting one still-active proposal;
- `POST /jobs/{jobId}/proposals/{proposalId}/accept` — requester accepts one submitted proposal after the authoritative escrow adjustment succeeds.

V1 allows one proposal row per worker/job. Withdrawal preserves the historical row instead of deleting it.

Competitors never receive other workers' proposal prices or messages. The proposal response exposes public user IDs only; email, exact location, verification documents, moderation data, wallet data and payment details are not part of this API. The acceptance-funding response is requester-only because it contains the requester's available wallet contribution to the selected escrow delta.

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

## Funding a higher accepted proposal

The acceptance-funding preflight is deliberately advisory. It does not reserve money, change proposal state or assign a worker. It reads the current held escrow amount and current wallet balance and reports:

- the amount already held;
- the selected proposal amount;
- how much of the positive delta the existing wallet could currently cover;
- the remaining payment shortfall;
- the Stripe charge required for that shortfall;
- whether the shortfall fits the existing single-payment safety boundary.

For example, if a 30.00 PLN job already has 30.00 PLN held, the chosen proposal is 42.00 PLN and the requester has 5.00 PLN available, the additional escrow requirement is 12.00 PLN. The preflight reports 5.00 PLN available from the wallet and a 7.00 PLN online shortfall.

The web client funds that shortfall through the existing generic wallet top-up path. It does not introduce a second payment ledger or a proposal-specific Stripe webhook. A successful Stripe PaymentIntent is still claimed exactly once in `payment_transactions` and credited as a normal `TOP_UP` by the signed webhook. After the credit becomes visible, the client calls the normal proposal-accept endpoint again; that endpoint re-locks and re-validates the job/proposal and atomically debits only the actual escrow delta.

The existing online top-up minimum is 1.00 PLN. If the remaining shortfall is smaller, Stripe charges 1.00 PLN and only the required delta is later moved into escrow; the surplus remains ordinary wallet balance. The existing 10,000.00 PLN single top-up maximum also applies. Larger shortfalls are not split or bypassed automatically: the requester must fund the wallet separately before retrying acceptance.

This separation is intentional. A Stripe webhook never chooses a worker. If the proposal is withdrawn, the job leaves `OPEN`, either user blocks the other, or any other acceptance invariant changes while payment is settling, the external payment can still safely finish as an ordinary wallet credit without resurrecting or accepting stale marketplace state.

## Concurrency and lifecycle

Proposal submission, withdrawal and acceptance lock the job row before mutating proposal state. This serializes proposal writes with final worker selection and prevents a proposal from being submitted after the job has concurrently moved out of `OPEN`.

The funding preflight intentionally does not take the acceptance lock for the duration of an external payment. Its answer is a UX quote, not authorization. The later `POST .../accept` remains authoritative and repeats owner, job-status, proposal-status, blocking and escrow checks inside the normal transaction.

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

Bilateral user blocking is enforced when a proposal is submitted and checked again both when funding is quoted and when the requester accepts it. A previously submitted proposal therefore cannot be used to start a new commercial interaction after either side blocks the other.

Historical proposal visibility is intentionally retained for accountability. Blocking does not delete transaction history.

Proposal mode does not grant exact-location access. Before assignment, workers still see only the same public job-location fields available through normal discovery. Exact addresses, routes and live tracking remain behind the existing participant/lifecycle authorization boundary.

The funding preflight does not expose the requester's total wallet balance; it returns only the amount of the required positive escrow delta that the wallet could currently contribute. It is available only to the job requester and does not expose Stripe secrets, PaymentIntent data or another user's financial state.

## Schema

Flyway `V35__job_assignment_proposals.sql`:

- adds `assignment_mode` and `price_negotiation_enabled` to `jobs` with backward-compatible defaults and database checks;
- creates `job_proposals` with optimistic versioning, one proposal per job/proposer, positive amount validation and indexed job/proposer lookups.

The acceptance-funding flow requires no additional migration. It reuses the existing escrow transaction, wallet ledger, Stripe payment ledger and proposal schema.

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
- before selection, the requester gets a server-derived funding preflight rather than client-side wallet arithmetic;
- when a higher proposal needs extra money, the existing Stripe Payment Element can fund only the remaining shortfall after currently available wallet funds;
- after Stripe success/processing, the UI waits for the signed webhook credit and only then retries the normal authoritative proposal acceptance;
- if state changed while payment settled, acceptance fails safely and the credited money remains in the requester's wallet;
- accepting a funded proposal updates the job immediately to the final price and normal active lifecycle;
- remaining submitted proposals are shown as rejected after another worker is selected.

The frontend never attempts to infer or display competitors' offers for workers. It relies on the server-side visibility contract of `GET /jobs/{jobId}/proposals`, so custom clients and normal UI follow the same privacy boundary.
