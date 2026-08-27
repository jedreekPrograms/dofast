# Saved search alerts

Saved-search alerts are opt-in notifications for newly published jobs that match a user's saved public discovery filters.

## Publication path

Publishing a job must stay bounded and transactional. `JobService.createJob` persists a single row in `job_publication_outbox` in the same database transaction as the job and its payment hold. It does **not** scan saved searches or send notifications inline.

If job creation rolls back, the outbox row rolls back with it. A committed job therefore has a durable publication event that can be processed later.

## Worker and matching

`JobPublicationAlertWorker` polls pending publication events. `JobPublicationAlertProcessor` locks a bounded batch and evaluates only saved searches with `alerts_enabled = true`.

The matcher uses the same public discovery dimensions stored by saved searches:

- case-insensitive query substring across title and description,
- exact leaf category or its direct parent category,
- minimum price,
- maximum price.

A publisher never receives an alert for their own job.

Exact coordinates, private address labels, route geometry and participant-only tracking data are deliberately excluded from matching and notification bodies.

## Delivery idempotency

`saved_search_alert_deliveries` has a unique `(saved_search_id, job_id)` constraint. The processor also checks for an existing delivery before sending the durable notification.

The publication event is marked processed only after all matching alerts in the transaction have been persisted. If processing fails, the transaction rolls back and the event remains pending for retry without duplicating already committed deliveries.

## Notification behavior

Matches create a durable `SAVED_SEARCH_MATCH` inbox notification linked to the public job. The notification may also be sent over the existing realtime channel. Users can mute realtime `SAVED_SEARCH_MATCH` delivery through notification preferences without deleting durable inbox history.

## User control

Saved searches default to alerts disabled. The saved-search page exposes an explicit per-preset toggle. Updating the toggle reuses the authenticated saved-search update endpoint and remains scoped to the current user.

## Database migration

Flyway `V22__saved_search_alert_outbox.sql` adds:

- `saved_searches.alerts_enabled`, default `false`,
- `job_publication_outbox`, one durable publication event per job,
- a partial index for pending events,
- `saved_search_alert_deliveries`, with a uniqueness constraint for idempotent delivery.
