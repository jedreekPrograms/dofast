# Authenticated routing provider abuse protection

Production routing can call an external provider and therefore has both capacity and direct monetary cost. Authentication alone is not an abuse boundary: a valid account must not be able to generate an unbounded number of provider calls.

## Application budget

The API applies a fixed-window budget per authenticated doFast account after JWT authentication has established the trusted `User` principal.

Only operations that can call the routing provider consume the budget:

- `POST /routing/quotes` costs 1 provider-call unit (`DRIVE` estimate),
- `GET /routing/quotes/{id}/mode-estimates` costs 2 provider-call units (`BICYCLE` and `WALK` estimates),
- `GET /routing/quotes/{id}` is a database-backed read and consumes no routing-provider budget.

The production default is 60 provider-call units per account per 60 seconds. When the budget is exhausted, the API returns `429 Too Many Requests` and a `Retry-After` header. Production stores the counter in the shared Redis backend; the plain local/IDE profile uses bounded in-memory state.

Configuration:

- `AUTHENTICATED_ROUTING_RATE_LIMIT_MAX_PROVIDER_CALLS` (default `60`),
- `AUTHENTICATED_ROUTING_RATE_LIMIT_WINDOW_SECONDS` (default `60`),
- `AUTHENTICATED_ROUTING_RATE_LIMIT_MAX_ENTRIES` (default `10000`).

The application deliberately keys this control by the authenticated persistent user id instead of `X-Forwarded-For`. This prevents a client from evading the provider-cost budget by rotating or spoofing proxy headers and avoids collapsing all users behind the production reverse proxy into one application bucket.

## Security and privacy properties

Routing quote ownership is enforced by `RouteQuoteService` before an existing quote or its mode estimates are returned. The limiter does not include coordinates, labels, place ids, tokens, email addresses, or route ids in its keys or responses. A rejected request therefore does not introduce a new location-data disclosure channel.

Unauthenticated requests are not turned into `429` responses by this filter; they continue through the security chain and are rejected by the normal authentication boundary. This avoids exposing a separate account-state signal before authentication.

## Multi-instance and provider controls

Every production API replica uses one atomic Redis account/provider budget. Implementation, privacy and failure details are in [DISTRIBUTED_RATE_LIMITING.md](DISTRIBUTED_RATE_LIMITING.md). This is not a replacement for provider-side quotas, provider cost alerts, trusted-ingress controls or a highly available Redis deployment.

Keep provider API keys server-side and restricted to the required API. Browser map keys and server routing keys must remain separate and independently restricted.

## Operational guidance

Alert on sustained `429` rates and on routing-provider quota/cost anomalies. Raising the application budget should be treated as a capacity/cost decision, not as a workaround for abusive clients. A provider outage should continue to use the existing routing-provider error path rather than being reported as rate limiting.
