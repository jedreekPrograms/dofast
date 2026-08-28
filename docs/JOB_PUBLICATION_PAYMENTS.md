# Job publication payments

## Goal

A requester should be able to publish a job without manually topping up the wallet first. doFast uses the available wallet balance and asks Stripe only for the missing amount. A job is public only after its complete budget is protected in escrow.

Example for a 70.00 PLN job with 25.00 PLN already in the wallet:

1. 25.00 PLN is reserved from the wallet for this publication.
2. The publication remains private; no `Job` row exists yet.
3. Stripe Payment Element asks for the 45.00 PLN shortfall.
4. A signed `payment_intent.succeeded` webhook is processed through the existing Stripe payment ledger.
5. In one server-side transaction the 45.00 PLN is credited, the 25.00 PLN publication reservation is released, the real job is created and the complete 70.00 PLN is locked as `HELD` escrow.
6. Only then does the job enter normal `OPEN` discovery and saved-search alert processing.

If the wallet already covers the whole budget, no Stripe payment is created. The existing `JobService.createJob` path immediately creates the job and locks the full escrow amount.

## Why pending payment is not a Job status

Payment preparation is represented by private `job_publications`, not by a `PAYMENT_PENDING` job status. This keeps incomplete publications out of discovery, chat, proposals, reports, saved jobs, attachments and every other feature whose authorization model assumes that a real job exists.

The pending row stores the submitted job payload only while payment is still possible. The payload is cleared after publication, cancellation, expiry or a safely handled late-payment state.

## Idempotency and concurrency

The client keeps one publication `requestId` across retries. The server combines it with the user id and stores a SHA-256 fingerprint of the submitted payload. Repeating the same request returns the same publication; using the same id for different job data is rejected.

Creation locks the user and wallet before deciding how much balance can be reserved. Two concurrent publications therefore cannot both spend the same wallet funds. Wallet mutations continue to use unique operation keys in the normal ledger.

Stripe PaymentIntent creation also uses a stable idempotency key derived from the publication id.

## Ledger semantics

For the 70 / 25 / 45 example the successful flow is deliberately expressed through the normal wallet ledger:

- `JOB_PUBLICATION_RESERVE -25.00`
- `TOP_UP +45.00` from the signed Stripe webhook
- `JOB_PUBLICATION_RELEASE +25.00`
- `ESCROW_LOCK -70.00`

This keeps the existing Stripe reconciliation invariant: every processed Stripe payment is represented by a `payment_transactions` claim and its corresponding `TOP_UP` wallet entry. The final available balance is 0.00 PLN and the held escrow is 70.00 PLN.

If the exact shortfall is below Stripe's configured minimum for this flow, doFast charges 1.00 PLN. The amount above the shortfall remains as ordinary wallet balance after the escrow lock.

A single publication payment is capped at 10,000.00 PLN by the application safety boundary.

## Cancellation and expiry

A payment-required publication is short-lived. The default payment window is 10 minutes and point-to-point jobs are additionally limited by the remaining lifetime of their server-side route quote.

The owner can cancel while the publication is still `PAYMENT_REQUIRED`. A scheduler also expires abandoned rows. Both paths restore the reserved wallet amount through the same idempotent `JOB_PUBLICATION_RELEASE` operation and clear the private payload.

The real job has not been created at this point, so no escrow cancellation, worker notification, tracking permission or public record needs to be undone.

## Late Stripe payments

A PaymentIntent can theoretically succeed after the user cancelled or after the publication window expired. The signed webhook is still accepted and the payment is claimed exactly once in the normal Stripe ledger, but it never resurrects a cancelled publication.

If payment reaches an expired publication before the expiry scheduler has cancelled it, doFast restores the reserved wallet amount, keeps the Stripe money in the wallet, clears the private payload and moves the publication to `PAYMENT_RECEIVED`. The user can start a fresh publication with the now-funded wallet.

For a cancelled publication, the reservation was already restored. A later successful Stripe webhook only credits the wallet and leaves the publication cancelled.

## Stripe and payment methods

The web application reuses one Stripe.js loader and Stripe Payment Element. The server creates the PaymentIntent with automatic payment methods. Which methods are displayed (for example card or other account-enabled methods) is controlled by Stripe eligibility/configuration rather than separate doFast endpoints.

Frontend confirmation is never treated as proof that a job is published. After Stripe reports success or processing, the UI polls the private publication endpoint. Only backend status `PUBLISHED`, reached after signed-webhook settlement and escrow creation, completes the flow.

## Privacy

Exact on-site addresses and point-to-point route details remain private under the same rules as ordinary job creation. Before funding, the pending payload is owner-only and never returned by the publication response. After a terminal publication state, the serialized payload is removed from `job_publications`.

Stripe receives payment metadata needed to bind the PaymentIntent to the authenticated user/publication; it does not receive the job description or exact route from this flow.
