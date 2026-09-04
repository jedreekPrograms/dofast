# User blocking

## Scope

Authenticated users can maintain a private block list through:

- `PUT /user-blocks/{userId}` — idempotently block another `ACTIVE` account.
- `DELETE /user-blocks/{userId}` — idempotently remove the block.
- `GET /user-blocks` — list only the caller's blocked accounts, newest first.

A block target is resolved through the `ACTIVE` account boundary before any existing relation is read. Missing and suspended user IDs therefore produce the same neutral not-found result and cannot be used to recover the target's nickname through this mutation. Existing private block history is retained and remains removable so a later suspension does not erase a user's established safety relationship.

The API deliberately returns only the blocked user's public identifier (`userId`, `nickname`) and the block timestamp. It does not expose email addresses, locations, routes, live tracking, payment data, moderation records or the other user's block state.

The authenticated web app exposes the caller's own list at `/blocked-users`. Users can unblock accounts there or block/unblock the counterpart directly from a job conversation. The chat UI hydrates only the caller's private block list, so it never reveals whether the other participant has blocked them.

## Persistence and invariants

Flyway `V31__user_blocks.sql` creates `user_blocks` with foreign keys to `users`, `ON DELETE CASCADE`, a unique `(blocker_id, blocked_user_id)` pair and a database check preventing self-blocks. Service validation rejects self-blocks before persistence and treats repeated block/unblock requests as idempotent operations for available targets.

## Interaction enforcement

`UserBlockService.isInteractionBlocked(first, second)` is a symmetric, server-side policy primitive: interaction is considered blocked if either side has blocked the other.

The audited behavior is intentionally different for new contact, public data and an existing commercial relationship:

| Surface | Result after a block in either direction | Enforcement boundary |
|---|---|---|
| Authenticated job lists, nearby search, recommendations and saved-search results | The other account's open jobs are excluded before pagination/limits. | Block-aware repository queries receive the persisted viewer ID. |
| Direct open-job detail, viewer attachments and job-based fee quote | A non-participant receives the same neutral not-found result as for a missing resource. | `JobVisibilityService` and `JobAttachmentAccessPolicy`. |
| Save job | A new bookmark is rejected before bookmark lookup/insert. | `SavedJobService`. |
| Instant acceptance | Assignment is rejected before job assignment, tracking initialization or notification. | Mandatory `UserBlockService` dependency in `JobService`. |
| Proposal submit | Submission is rejected before duplicate lookup, insert or notification. | `JobProposalService.submit`. |
| Proposal funding quote | Quote is rejected before escrow or wallet reads. | `JobProposalService.getAcceptanceFunding`. |
| Proposal acceptance | Selection is rejected before escrow adjustment, assignment, proposal writes, tracking or notification. | `JobProposalService.accept`. |
| Chat send, including an idempotent retry | Delivery is rejected before message, notification and realtime side effects. | `ChatService.sendMessage`. |
| Saved-search alert | Direct alert delivery is suppressed; the outbox item still completes. | `JobPublicationAlertProcessor`. |
| Review after a completed job | The accountability record remains writable/public, but the direct notification is suppressed. | `ReviewService`. |
| Existing chat/job/proposal/attachment history | Participant history remains readable. | Existing participant-scoped repository and access policies. |
| Completion, cancellation, expense, escrow, tracking and dispute/report flows for an existing job | The block does not cancel the job or bypass/disable its financial, safety and accountability lifecycle. | Existing participant, state and ownership policies. |
| Public profile and received reviews | The same sanitized `ACTIVE`-only projection remains anonymous and public. | Public-profile/review DTOs; a block is not presented as secrecy for data available without an account. |

The matrix is covered by focused tests for the symmetric primitive, database viewer propagation, direct-detail/attachment visibility, instant acceptance, all three proposal gates, saved jobs, chat, alerts and review-notification suppression. `JobService` cannot be constructed without its blocking and expense policy dependencies, preventing a test or alternate wiring path from silently disabling either rule.

Chat message delivery enforces this policy before any message row is inserted, notification is created or realtime event is published. The same check is also applied to retries using an existing `clientMessageId`, so blocking cannot be bypassed through the idempotency path. Existing chat history remains readable for job participants; blocking prevents new direct communication rather than deleting historical evidence or changing job lifecycle state.

Accepting an open job also enforces the symmetric block policy before the worker is assigned. A blocked relationship therefore cannot start a new job relationship, initialize live tracking or create the `JOB_ACCEPTED` notification. The rejection message is intentionally neutral and does not disclose which side created the block.

Saved-search alert delivery also applies the same symmetric check after a search matches but before any delivery row or notification is created. Users therefore do not receive automated `SAVED_SEARCH_MATCH` notifications for new jobs published by an account when either side has blocked the other. The outbox event is still processed normally so a blocked relationship cannot leave publication work stuck or repeatedly retried.

Saving an open job to the private shortlist also enforces the symmetric block policy before a bookmark lookup or insert. A blocked relationship therefore cannot create a new saved-job association, and the rejection remains intentionally neutral about which side created the block.

For authenticated non-participants, direct `GET /jobs/{id}` detail access also applies the symmetric policy. A blocked relationship is surfaced as the same neutral `Zlecenie nie istnieje` result used for a missing job, so the endpoint does not disclose which side created the block. Unauthenticated public reads retain their existing behavior, while the job owner and assigned worker retain access to their own job record so a later block cannot break an already-started lifecycle, dispute or accountability flow.

Authenticated discovery applies the same symmetric policy directly inside database queries for paginated `GET /jobs`, geospatial `GET /jobs/nearby` and private saved-search radius results. Jobs created by an account involved in either direction of a block are excluded before pagination, distance ordering and result limits are applied, so blocked rows cannot distort page totals or consume nearby/saved-search result slots. Anonymous public discovery keeps the existing behavior because it has no authenticated block context.

When the caller has blocked the selected chat counterpart, the web composer is disabled immediately and the draft is discarded. This is UX only: the backend remains the source of truth and rejects delivery if either participant has blocked the other, including a reverse block that is intentionally not exposed to the caller.

## Reviews after completed jobs

Blocking does not remove review eligibility for a completed job and does not hide or delete an existing review. Reviews are part of the accountability record of a transaction that already happened, so either participant can still submit an otherwise eligible review even if a block was created later.

If either participant has blocked the other, the review is still persisted normally but the direct `REVIEW_RECEIVED` notification is suppressed. This keeps the marketplace accountability signal while ensuring a block does not reopen a new notification/contact channel between the accounts.

Existing active jobs are not cancelled merely because either participant later creates a block. Their escrow, completion/dispute lifecycle and participant-only location authorization continue to follow the dedicated job rules; blocking prevents new interaction surfaces rather than silently mutating financial or safety-critical state.

New private interaction surfaces must reuse the symmetric server-side policy instead of trusting client-side hidden controls. New truly public surfaces must make their anonymous visibility explicit. Blocking does not alter escrow, active job state, location access or moderation records by itself; those remain governed by their existing participant and lifecycle authorization rules.
