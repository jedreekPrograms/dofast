# Public authentication rate limiting

Public authentication endpoints are protected by an in-process fixed-window limiter keyed by client address and endpoint.

Protected POST endpoints include registration, password login, Google/Apple login and challenge creation, refresh, password recovery/reset, and email-verification resend/verify. The default production policy is 30 requests per 60 seconds per client address per endpoint, with at most 10,000 active windows. When the capacity bound is reached and expired windows cannot be reclaimed, new keys fail closed with HTTP `429 Too Many Requests`.

A rejected request returns JSON `{\"status\":429,\"error\":\"Too Many Requests\"}` and a `Retry-After` header. Password-recovery enumeration resistance is preserved because limiting happens before account lookup and the response does not disclose account existence.

Configuration:

- `PUBLIC_AUTH_RATE_LIMIT_MAX_REQUESTS` (default `30`)
- `PUBLIC_AUTH_RATE_LIMIT_WINDOW_SECONDS` (default `60`)
- `PUBLIC_AUTH_RATE_LIMIT_MAX_ENTRIES` (default `10000`)
- `PUBLIC_AUTH_RATE_LIMIT_TRUST_FORWARDED_FOR` (default `false`)

`X-Forwarded-For` is intentionally ignored unless `PUBLIC_AUTH_RATE_LIMIT_TRUST_FORWARDED_FOR=true`. Enable that option only when the API is reachable exclusively through a trusted reverse proxy that overwrites/sanitizes the header. Directly exposing the API while trusting client-supplied forwarding headers lets attackers rotate spoofed addresses and bypass the limiter.

This limiter is a launch abuse-control layer, not a distributed quota service. In a multi-instance deployment, enforce an additional rate limit at the trusted ingress/API gateway or migrate the counter store to a shared backend so limits apply across all API replicas.
