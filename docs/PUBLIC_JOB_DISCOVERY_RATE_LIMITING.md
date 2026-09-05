# Public job discovery rate limiting

`GET /jobs` and `GET /jobs/nearby` are intentionally public so visitors can discover marketplace work before signing in. They also execute database filtering, and `/jobs/nearby` performs geospatial work, so they must have an explicit application-level abuse budget.

## Application boundary

`PublicJobDiscoveryRateLimitFilter` enforces a fixed-window budget independently for each client address and endpoint. The production default is 120 requests per 60 seconds, shared atomically across API replicas through Redis. Exhausted clients receive HTTP `429 Too Many Requests` with `Retry-After`.

The limiter covers only `GET /jobs` and `GET /jobs/nearby`; authenticated job mutation endpoints and cheap category metadata are outside this budget. Existing controller validation still bounds query text, pagination, nearby radius and nearby result count.

## Client address trust

`X-Forwarded-For` is ignored by default. This prevents direct clients from evading the limiter by supplying arbitrary forwarded addresses. `PUBLIC_JOB_DISCOVERY_RATE_LIMIT_TRUST_FORWARDED_FOR=true` may be enabled only when the API is reachable exclusively through a trusted reverse proxy that strips inbound forwarding headers and writes the canonical client chain itself.

Production stores a keyed digest, not the raw address/path pair, in Redis. A horizontally scaled deployment must still enforce complementary connection/request controls at the trusted ingress or CDN.

## Configuration

- `PUBLIC_JOB_DISCOVERY_RATE_LIMIT_MAX_REQUESTS` (default `120`)
- `PUBLIC_JOB_DISCOVERY_RATE_LIMIT_WINDOW_SECONDS` (default `60`)
- `PUBLIC_JOB_DISCOVERY_RATE_LIMIT_MAX_ENTRIES` (default `10000`)
- `PUBLIC_JOB_DISCOVERY_RATE_LIMIT_TRUST_FORWARDED_FOR` (default `false`)

The local in-memory fallback is bounded. Production Redis has a hard memory cap and never evicts live security counters; backend write failures return `503` without entering the controller. See [DISTRIBUTED_RATE_LIMITING.md](DISTRIBUTED_RATE_LIMITING.md).

## Verification

Unit tests cover rejection and `Retry-After`, endpoint/client isolation, forwarded-header trust, and exclusion of unrelated paths/methods. A real-Redis integration test proves cross-instance sharing, concurrency safety and namespace/key privacy. Production CI continues to run Maven verification, frontend tests/lint/build, Compose contract validation and runtime container smokes before merge.
