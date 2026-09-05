# Authenticated operation abuse protection

Authentication is not an abuse boundary. A valid account must not be able to produce unbounded provider cost, financial dispatches, storage work, settlement transitions or amplified queries. The API therefore applies one shared fixed-window cost budget per authenticated doFast account after JWT authentication establishes the trusted `User` principal.

## Shared account budget

The production default is **240 cost units per account per 60 seconds**. Operations share that budget across endpoint families, so rotating between payment, job, attachment, chat and administration routes does not reset the allowance.

| Cost | Operation class | Audited examples |
|---:|---|---|
| `20` | External provider or financial dispatch | Routing provider estimates, Stripe PaymentIntent/refund, publication funding, payout onboarding/request and admin payout retry. |
| `10` | Storage-heavy or settlement transition | Attachment upload/download, job/publication creation and cancellation, job completion/acceptance, proposal acceptance, expense claim, cancellation approval, dispute resolution and admin enforcement. |
| `5` | Amplified query, history or aggregate read | Recommendations, saved-search execution, user histories, chat evidence, payout eligibility and finance reconciliation. |
| `1` | Every other private `POST`, `PUT`, `PATCH` or `DELETE` | Future authenticated mutations inherit this baseline automatically even before an explicit weight is assigned. |
| `0` | Anonymous allowlist and ordinary authenticated reads | Public routes remain owned by their dedicated IP limits; low-cost reads do not consume this budget. |

`AuthenticatedOperationRateLimitPolicy` is the source of truth for the 41 explicitly weighted operations. `AuthenticatedOperationRateLimitPolicyTest` discovers the controller surface and fails when:

- a private mutation can bypass the baseline budget,
- a weighted rule no longer resolves to exactly one real controller endpoint,
- two weighted rules overlap on one endpoint, or
- an anonymous endpoint starts consuming the authenticated-account budget.

When the budget is exceeded, the API returns `429 Too Many Requests` with a `Retry-After` header and does not enter the controller. Requests without a trusted authenticated `User` are left to the normal security chain, so the limiter does not replace `401`/`403` authorization semantics. A transient authenticated principal without a persistent id uses one deliberately shared fail-closed bucket.

## Layered routing budget

Provider-backed routing calls consume both this cross-operation budget and the narrower routing-provider budget described in [AUTHENTICATED_ROUTING_RATE_LIMITING.md](AUTHENTICATED_ROUTING_RATE_LIMITING.md). The shared budget prevents cross-endpoint abuse; the routing budget independently represents the number of downstream route calculations. Cached `GET /routing/quotes/{id}` reads consume neither provider allowance nor the shared costly-operation allowance.

## Configuration

- `AUTHENTICATED_OPERATION_RATE_LIMIT_MAX_COST_UNITS` (default `240`),
- `AUTHENTICATED_OPERATION_RATE_LIMIT_WINDOW_SECONDS` (default `60`),
- `AUTHENTICATED_OPERATION_RATE_LIMIT_MAX_ENTRIES` (default `10000`).

The maximum budget cannot be configured below the largest single-operation cost. State is bounded; if all account buckets are occupied and none has expired, a new account fails closed with `429` instead of growing process memory without limit. Public authentication and discovery keep their separate IP-and-endpoint buckets.

## Deployment boundary

This limiter is intentionally in-process and protects each API instance. It does not claim a cluster-wide guarantee. Before horizontal scaling, replace or back it with an atomic shared store or gateway policy while preserving the same account key, weights, response semantics and contract tests. Provider-side hard quotas, Stripe controls and monitoring remain independent layers.

Alert on sustained `429` rates by operation class and account-safe aggregate, not on raw identifiers. Raising weights or the account budget is a capacity and fraud decision and must be accompanied by a review of provider quotas and realistic load results.
