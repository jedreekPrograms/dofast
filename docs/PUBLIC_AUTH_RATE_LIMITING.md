# Public authentication rate limiting

Public authentication endpoints are protected by a fixed-window limiter keyed by client address and endpoint. Production uses the shared atomic Redis backend; the plain local/IDE profile uses the bounded in-memory backend.

Protected POST endpoints include registration, password login, Google/Apple login and challenge creation, refresh, password recovery/reset, and email-verification resend/verify. The default production policy is 30 requests per 60 seconds per client address per endpoint. Every API replica consumes the same Redis counter.

A rejected request returns JSON `{\"status\":429,\"error\":\"Too Many Requests\"}` and a `Retry-After` header. Password-recovery enumeration resistance is preserved because limiting happens before account lookup and the response does not disclose account existence.

Configuration:

- `PUBLIC_AUTH_RATE_LIMIT_MAX_REQUESTS` (default `30`)
- `PUBLIC_AUTH_RATE_LIMIT_WINDOW_SECONDS` (default `60`)
- `PUBLIC_AUTH_RATE_LIMIT_MAX_ENTRIES` (default `10000`)
- `PUBLIC_AUTH_RATE_LIMIT_TRUST_FORWARDED_FOR` (default `false`)

`X-Forwarded-For` is intentionally ignored unless `PUBLIC_AUTH_RATE_LIMIT_TRUST_FORWARDED_FOR=true`. Enable that option only when the API is reachable exclusively through a trusted reverse proxy that overwrites/sanitizes the header. Directly exposing the API while trusting client-supplied forwarding headers lets attackers rotate spoofed addresses and bypass the limiter.

Redis keys contain a keyed digest rather than the raw address or endpoint. Backend outages fail closed with `503` and do not enter authentication handlers. See [DISTRIBUTED_RATE_LIMITING.md](DISTRIBUTED_RATE_LIMITING.md) for atomicity, privacy, configuration and the remaining ingress/HA boundary.
