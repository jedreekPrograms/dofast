# Notifications

## In-app notification model

Notifications are persisted in PostgreSQL before any realtime delivery is attempted. The notification inbox remains the durable source of truth, while WebSocket delivery is only an immediate UX enhancement.

Authenticated users can list notifications, inspect the unread count, mark one notification read, or mark all notifications read under `/notifications`.

## Realtime preferences

`GET /notifications/preferences` returns the notification types that the user is allowed to mute in realtime plus the subset currently muted. `PUT /notifications/preferences` replaces that muted subset.

The first configurable types are:

- `CHAT_MESSAGE`
- `REVIEW_RECEIVED`
- `SAVED_SEARCH_MATCH`

Muting one of these types suppresses only the realtime WebSocket popup. The event is still persisted and remains visible in the notification centre, so the user cannot silently lose history.

Transactional and safety-sensitive types are intentionally not configurable. Job lifecycle, cancellation, dispute, verification and other critical notifications continue to be persisted and delivered realtime. Attempts to submit a critical type in `mutedTypes` are rejected with HTTP 400.

## Persistence and privacy

Flyway migration `V20__notification_preferences.sql` stores muted realtime types in `notification_preferences`. Rows are scoped by `user_id` and constrained to one row per `(user_id, notification_type)`. The user foreign key cascades on account deletion.

Every user-facing inbox, unread-count, read-state and preference operation requires an authenticated principal with a persisted user id before any notification repository is accessed. Missing or transient identities fail closed without reading or mutating private notification state.

Preference reads and writes use the authenticated principal only. There is no endpoint for reading or modifying another user's settings.

## Frontend

The notification centre exposes switches for the configurable realtime types. A disabled switch means the realtime popup is muted; it does not remove the underlying notification from the inbox.
