# Stripe Connect payout event ordering

Signed Stripe Connect payout webhooks can be delivered out of order. doFast therefore treats `Event.created` as the ordering watermark for terminal payout state observations.

## Rules

- `payout.paid`, `payout.failed`, and terminal `payout.updated` events are still validated against the stored payout id, user id, transfer id, amount, currency, and connected account before ordering is considered.
- `payout_requests.provider_state_event_created_at` stores the greatest accepted signed Stripe event timestamp for payout state.
- A webhook with `Event.created` older than that watermark returns a successful stale/no-op result. It cannot reverse the platform transfer, restore wallet funds, or rewrite the terminal payout state.
- A newer event may advance the watermark only in the same database transaction as provider settlement. If local settlement rolls back, the watermark rolls back too.
- Provider polling reconciliation does not invent a Stripe event timestamp and therefore continues to use the existing provider-state validation and terminal-state conflict rules.
- Equal-second contradictory terminal events are not silently ordered because Stripe timestamps have second resolution; the terminal-state conflict guard remains fail-closed rather than guessing a winner.
- Terminal compatibility is checked before any compensating Stripe transfer reversal. A failure/cancellation observation cannot cause external money movement when the local payout is already `PAID` or otherwise incompatible with that outcome.
- Repeated failure observations for an already `FAILED` payout do not issue another transfer reversal; provider settlement may still persist provider-event idempotency/audit state.

## Crash and retry safety

Failed/canceled payouts reverse the original Stripe platform transfer before wallet restoration only while the local payout is still `SUBMITTED`. The reversal uses the existing stable Stripe idempotency key, so a provider-side success followed by a local transaction rollback can be retried safely.

Ordering and terminal-state preflight both run before reversal. This prevents delayed, same-second, or otherwise contradictory failure observations from triggering external compensation after a successful payout has already become terminal locally. The generic settlement service remains the authoritative state transition layer and re-checks the same terminal compatibility under the payout row lock.

Flyway `V54__stripe_payout_event_ordering.sql` adds the nullable BIGINT ordering watermark. Existing payouts start without a watermark and remain compatible with reconciliation and previously accepted terminal state. No additional schema migration is required for the terminal preflight guard.
