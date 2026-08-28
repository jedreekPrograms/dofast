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

`UserBlockService.isInteractionBlocked(first, second)` provides a symmetric, server-side policy primitive: interaction is considered blocked if either side has blocked the other. This PR intentionally establishes the persistence/API boundary first. Chat, discovery/profile actions and other interaction surfaces should call this server-side policy rather than trusting client-side hidden controls.

This separation keeps the migration and ownership/privacy rules atomic and testable before individual product surfaces are switched over in subsequent changes.
