# User blocking

## Scope

Authenticated users can maintain a private block list through:

- `PUT /user-blocks/{userId}` — idempotently block another account.
- `DELETE /user-blocks/{userId}` — idempotently remove the block.
- `GET /user-blocks` — list only the caller's blocked accounts, newest first.

The API deliberately returns only the blocked user's public identifier (`userId`, `nickname`) and the block timestamp. It does not expose email addresses, locations, routes, live tracking, payment data, moderation records or the other user's block state.

The authenticated web app exposes the caller's own list at `/blocked-users`. Users can unblock accounts there or block/unblock the counterpart directly from a job conversation. The chat UI hydrates only the caller's private block list, so it never reveals whether the other participant has blocked them.

## Persistence and invariants

Flyway `V31__user_blocks.sql` creates `user_blocks` with foreign keys to `users`, `ON DELETE CASCADE`, a unique `(blocker_id, blocked_user_id)` pair and a database check preventing self-blocks. Service validation rejects self-blocks before persistence and treats repeated block/unblock requests as idempotent operations.

## Interaction enforcement

`UserBlockService.isInteractionBlocked(first, second)` is a symmetric, server-side policy primitive: interaction is considered blocked if either side has blocked the other.

Chat message delivery enforces this policy before any message row is inserted, notification is created or realtime event is published. The same check is also applied to retries using an existing `clientMessageId`, so blocking cannot be bypassed through the idempotency path. Existing chat history remains readable for job participants; blocking prevents new direct communication rather than deleting historical evidence or changing job lifecycle state.

Accepting an open job also enforces the symmetric block policy before the worker is assigned. A blocked relationship therefore cannot start a new job relationship, initialize live tracking or create the `JOB_ACCEPTED` notification. The rejection message is intentionally neutral and does not disclose which side created the block.

Saved-search alert delivery also applies the same symmetric check after a search matches but before any delivery row or notification is created. Users therefore do not receive automated `SAVED_SEARCH_MATCH` notifications for new jobs published by an account when either side has blocked the other. The outbox event is still processed normally so a blocked relationship cannot leave publication work stuck or repeatedly retried.

Saving an open job to the private shortlist also enforces the symmetric block policy before a bookmark lookup or insert. A blocked relationship therefore cannot create a new saved-job association, and the rejection remains intentionally neutral about which side created the block.

When the caller has blocked the selected chat counterpart, the web composer is disabled immediately and the draft is discarded. This is UX only: the backend remains the source of truth and rejects delivery if either participant has blocked the other, including a reverse block that is intentionally not exposed to the caller.

## Reviews after completed jobs

Blocking does not remove review eligibility for a completed job and does not hide or delete an existing review. Reviews are part of the accountability record of a transaction that already happened, so either participant can still submit an otherwise eligible review even if a block was created later.

If either participant has blocked the other, the review is still persisted normally but the direct `REVIEW_RECEIVED` notification is suppressed. This keeps the marketplace accountability signal while ensuring a block does not reopen a new notification/contact channel between the accounts.

Existing active jobs are not cancelled merely because either participant later creates a block. Their escrow, completion/dispute lifecycle and participant-only location authorization continue to follow the dedicated job rules; blocking prevents new interaction surfaces rather than silently mutating financial or safety-critical state.

Future discovery/profile actions and other interaction surfaces should reuse the same server-side policy instead of trusting client-side hidden controls. Blocking does not alter escrow, active job state, location access or moderation records by itself; those remain governed by their existing participant and lifecycle authorization rules.
