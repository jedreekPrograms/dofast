# Public job discovery rate limiting

`GET /jobs` and `GET /jobs/nearby` are intentionally public so visitors can discover marketplace work before signing in. They also execute database filtering, and `/jobs/nearby` performs geospatial work, so they must have an explicit application-level abuse budget.

## Application boundary

`PublicJobDiscoveryRateLimitFilter` enforces a fixed-window budget independently for each client address and endpoint. The production defaults are 120 requests per 60 seconds with at most 10,000 active buckets per API instance. Exhausted clients receive HTTP `429 Too Many Requests` with `Retry-After`.

The limiter covers only `GET /jobs` and `GET /jobs/nearby`; authenticated job mutation endpoints and cheap category metadata are outside this budget. Existing controller validation still bounds query text, pagination, nearby radius and nearby result count.

## Client address trust

`X-Forwarded-For` is ignored by default. This prevents direct clients from evading the limiter by supplying arbitrary forwarded addresses. `PUBLIC_JOB_DISCOVERY_RATE_LIMIT_TRUST_FORWARDED_FOR=true` may be enabled only when the API is reachable exclusively through a trusted reverse proxy that strips inbound forwarding headers and writes the canonical client chain itself.

For the current single-instance deployment this application limiter provides an additional safety boundary. A horizontally scaled deployment must also enforce a shared rate limit at the ingress/CDN or move the accounting to a shared store; per-instance in-memory buckets alone are not a global quota.

## Configuration

- `PUBLIC_JOB_DISCOVERY_RATE_LIMIT_MAX_REQUESTS` (default `120`)
- `PUBLIC_JOB_DISCOVERY_RATE_LIMIT_WINDOW_SECONDS` (default `60`)
- `PUBLIC_JOB_DISCOVERY_RATE_LIMIT_MAX_ENTRIES` (default `10000`)
- `PUBLIC_JOB_DISCOVERY_RATE_LIMIT_TRUST_FORWARDED_FOR` (default `false`)

The in-memory map is bounded. When capacity is exhausted and cleanup cannot reclaim expired buckets, requests requiring a new bucket fail closed with `429` rather than allowing unbounded memory growth.

## Verification

Unit tests cover rejection and `Retry-After`, endpoint/client isolation, forwarded-header trust, and exclusion of unrelated paths/methods. Production CI continues to run Maven verification, frontend tests/lint/build, Compose contract validation and runtime container smokes before merge.
