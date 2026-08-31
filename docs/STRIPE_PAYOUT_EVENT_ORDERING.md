# Stripe Connect payout event ordering

Signed Stripe Connect payout webhooks can be delivered out of order. doFast therefore treats `Event.created` as the ordering watermark for terminal payout state observations.

## Rules

- `payout.paid`, `payout.failed`, and terminal `payout.updated` events are still validated against the stored payout id, user id, transfer id, amount, currency, and connected account before ordering is considered.
- `payout_requests.provider_state_event_created_at` stores the greatest accepted signed Stripe event timestamp for payout state.
- A webhook with `Event.created` older than that watermark returns a successful stale/no-op result. It cannot reverse the platform transfer, restore wallet funds, or rewrite the terminal payout state.
- A newer event may advance the watermark only in the same database transaction as provider settlement. If local settlement rolls back, the watermark rolls back too.
- Provider polling reconciliation does not invent a Stripe event timestamp and therefore continues to use the existing provider-state validation and terminal-state conflict rules.
- Equal-second contradictory terminal events are not silently ordered because Stripe timestamps have second resolution; the existing terminal-state conflict guard remains fail-closed rather than guessing a winner.

## Crash and retry safety

Failed/canceled payouts still reverse the original Stripe platform transfer before wallet restoration. The reversal uses the existing stable Stripe idempotency key. The new ordering guard runs before reversal, so a delayed older failure event cannot trigger external compensation after a newer terminal observation has already been accepted.

Flyway `V54__stripe_payout_event_ordering.sql` adds the nullable BIGINT ordering watermark. Existing payouts start without a watermark and remain compatible with reconciliation and previously accepted terminal state.
