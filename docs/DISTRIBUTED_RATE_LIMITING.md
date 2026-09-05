# Distributed application rate limiting

Production uses one private Redis backend for every application-level abuse budget. All API replicas therefore observe the same counters for public authentication, public job discovery, authenticated routing-provider calls, authenticated costly operations and inbound WebSocket frames.

## Enforcement model

`RedisFixedWindowRateLimiter` performs increment, first-window expiry and the allow/reject decision in one Redis Lua script. Concurrent requests cannot read and update a counter separately, and opening another API replica does not create another allowance. Window expiry and `Retry-After` use Redis server TTL, so modest clock skew between API nodes cannot split or extend a budget.

The five namespaces are independent:

| Namespace | Key source | Budget owner |
|---|---|---|
| `public-auth` | trusted client address + endpoint | public authentication filter |
| `public-job-discovery` | trusted client address + endpoint | public discovery filter |
| `authenticated-routing` | persistent account id | downstream routing budget |
| `authenticated-operation` | persistent account id | weighted costly-operation budget |
| `websocket-inbound` | normalized authenticated principal | inbound STOMP budget |

Raw account ids, addresses and endpoint paths are never stored in Redis keys. The API derives a deterministic HMAC-SHA-256 digest from the namespace and raw key using `RATE_LIMIT_KEY_HMAC_SECRET`; only the namespace and digest are visible in Redis. The secret must contain at least 32 bytes and is required by the production profile.

## Failure policy

The limiter fails closed. If Redis times out, rejects an increment or returns an invalid script result, a limited HTTP request receives `503 Service Unavailable` plus `Retry-After: 1` and never enters its controller. An inbound WebSocket frame is rejected with `AccessDeniedException`. Normal budget exhaustion remains `429 Too Many Requests` with the remaining Redis TTL in `Retry-After`.

Redis rate-limit data is deliberately ephemeral: Compose disables snapshots and append-only persistence. Losing counters during a Redis restart temporarily resets windows but cannot corrupt business data. Redis is capped at 128 MiB with `noeviction`; capacity exhaustion rejects writes and therefore follows the same fail-closed path instead of silently evicting active security counters. The service has no published host port and API startup waits for its authenticated healthcheck.

## Configuration

The plain local/IDE profile defaults to `RATE_LIMIT_BACKEND=memory`, which keeps tests and development independent of infrastructure. Both root Compose and the production profile select `redis` explicitly.

- `RATE_LIMIT_BACKEND`: `memory` or `redis`; production is hard-wired to `redis`.
- `RATE_LIMIT_REDIS_HOST` / `RATE_LIMIT_REDIS_PORT`: Redis endpoint.
- `RATE_LIMIT_REDIS_PASSWORD`: required in production.
- `RATE_LIMIT_REDIS_KEY_PREFIX`: deployment/version prefix used to isolate environments.
- `RATE_LIMIT_KEY_HMAC_SECRET`: independent high-entropy secret used only to pseudonymize keys.
- `RATE_LIMIT_REDIS_CONNECT_TIMEOUT` / `RATE_LIMIT_REDIS_TIMEOUT`: fail-closed connection and command bounds; both default to two seconds.
- `RATE_LIMIT_REDIS_HEALTH_ENABLED`: local health opt-in; production enables it.

Rotate the HMAC secret or change the key prefix during a controlled deployment. Either change creates fresh buckets, so it should be treated as a temporary quota reset. Do not reuse JWT, database, SMTP or provider credentials as the HMAC secret.

## Verification and remaining boundary

CI starts a real Redis service during Maven verification. The integration test creates two limiter instances, proves that they share an atomic allowance under concurrent load, verifies namespace isolation and confirms that raw identities are absent from stored keys. Unit tests cover key derivation, server TTL mapping and fail-closed HTTP/WebSocket behavior. The production Compose contract requires both Redis secrets, private networking, health-gated API startup, disabled persistence and `noeviction`.

The Compose Redis service provides a shared counter store, not Redis high availability. A multi-node production rollout still needs a managed/HA Redis deployment, resource and latency alerts, and trusted-ingress connection limits. Provider-side quotas, WAF/CDN controls and outer-gateway limits remain independent layers.
