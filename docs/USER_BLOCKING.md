# User blocking

## Scope

Authenticated users can maintain a private block list through:

- `PUT /user-blocks/{userId}` — idempotently block another account.
- `DELETE /user-blocks/{userId}` — idempotently remove the block.
- `GET /user-blocks` — list only the caller's blocked accounts, newest first.

The API deliberately returns only the blocked user's public identifier (`userId`, `nickname`) and the block timestamp. It does not expose email addresses, locations, routes, live tracking, payment data, moderation records or the other user's block state.

## Persistence and invariants

Flyway `V31__user_blocks.sql` creates `user_blocks` with foreign keys to `users`, `ON DELETE CASCADE`, a unique `(blocker_id, blocked_user_id)` pair and a database check preventing self-blocks. Service validation rejects self-blocks before persistence and treats repeated block/unblock requests as idempotent operations.

## Interaction enforcement

`UserBlockService.isInteractionBlocked(first, second)` is a symmetric, server-side policy primitive: interaction is considered blocked if either side has blocked the other.

Chat message delivery now enforces this policy before any message row is inserted, notification is created or realtime event is published. The same check is also applied to retries using an existing `clientMessageId`, so blocking cannot be bypassed through the idempotency path. Existing chat history remains readable for job participants; blocking prevents new direct communication rather than deleting historical evidence or changing job lifecycle state.

Future discovery/profile actions and other interaction surfaces should reuse the same server-side policy instead of trusting client-side hidden controls. Blocking does not alter escrow, active job state, location access or moderation records by itself; those remain governed by their existing participant and lifecycle authorization rules.
