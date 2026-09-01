# Route quote privacy boundary

Route quotes contain execution-sensitive location data. A quote can include exact origin, intermediate-stop and destination coordinates, private address labels, provider place IDs and encoded route geometry. Those fields are never a public discovery contract.

## Ownership rule

Authenticated user-facing operations resolve a quote by both quote id and authenticated user id. A quote owned by another account is therefore indistinguishable from a quote that does not exist: both result in the same neutral not-found response.

This rule applies to:

- reading `GET /routing/quotes/{id}`;
- calculating `GET /routing/quotes/{id}/mode-estimates`;
- consuming a quote while creating a job.

The mode-estimate path performs the ownership lookup before any additional routing-provider calls, so a leaked foreign quote UUID cannot be used either to confirm that exact-location data exists or to spend another user's routing-provider budget.

## Locking and trusted internal flows

Quote consumption remains protected by a pessimistic write lock, but the lock query is owner-scoped `(quoteId, userId)`. This preserves the single-use concurrency invariant without revealing quote existence to another authenticated user.

A separate global pessimistic lookup remains available for trusted internal publication/payment settlement. That internal path receives the quote id from durable server-owned publication state rather than directly authorizing an arbitrary user-supplied quote id. Keeping these two repository methods separate prevents privacy hardening from weakening crash recovery or Stripe settlement.

## Verification

Unit coverage verifies that an outsider:

- receives the same not-found result for an existing foreign quote as for a missing quote;
- cannot retrieve exact coordinates, private labels, place IDs or encoded geometry;
- cannot trigger bicycle/walking provider estimates for a foreign quote;
- cannot consume or mutate a foreign quote.

Owner coverage verifies that the legitimate owner still receives the exact execution data required to create and execute the job. Existing multi-stop, PostGIS, publication-payment and full container runtime smokes remain regression gates for the surrounding route-to-job flow.
