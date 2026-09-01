# WebSocket abuse protection

doFast applies transport-level and application-level abuse controls to WebSocket/STOMP traffic in addition to origin and authorization policy.

## Ingress connection and handshake limits

The production Nginx gateway rejects abusive traffic before it can consume Spring/STOMP capacity:

- native `/ws` handshakes are limited to 5 requests/second per direct peer IP with a burst of 10;
- native `/ws` concurrent connections are capped at 8 per direct peer IP;
- SockJS transport requests use a higher 30 requests/second budget with a burst of 60 because one logical SockJS session can require multiple HTTP requests;
- SockJS transport concurrency is capped at 20 per direct peer IP;
- rejected requests return HTTP `429`;
- WebSocket/SockJS ingress request bodies are capped at 64 KiB;
- proxy buffering is disabled and upstream read/send timeouts are bounded.

These controls are intentionally keyed by Nginx `$remote_addr`, not by a client-supplied forwarding header. The gateway does forward `X-Real-IP` and `X-Forwarded-For` to the API for observability, but those headers do not decide the gateway limits. If another load balancer or CDN is placed in front of this Nginx instance, production infrastructure must restore a trustworthy client address before relying on per-IP enforcement; do not blindly trust arbitrary `X-Forwarded-For` input.

The runtime CI smoke opens eight real upgraded WebSocket connections from one source, verifies that the ninth is rejected with `429`, then verifies that a rapid handshake burst also reaches the request-rate limit. This exercises the built image and gateway rather than only checking configuration text.

## STOMP authorization boundary

WebSocket is currently a **server-to-client delivery channel**. Clients may authenticate and subscribe only to explicitly authorized destinations:

- job participants may subscribe to their `/topic/chat/{jobId}` topic;
- the job owner/assigned worker may subscribe to `/topic/tracking/{jobId}` according to the tracking access policy;
- an authenticated account may subscribe to its own `/user/queue/notifications` destination.

Authenticated client `SEND` frames are rejected before the simple broker or application destination can process them. This is deliberate: allowing clients to publish directly to `/topic` or `/queue` would bypass REST ownership and business invariants and could inject forged chat/tracking payloads into other users' subscriptions. The same fail-closed rule currently applies to `/dofastapp/**`; if server-side `@MessageMapping` handlers are introduced later, every client-writable destination must be explicitly allow-listed and authorized before `SEND` is enabled.

### Credential-bound WebSocket sessions

A successful STOMP `CONNECT` is bound to the exact current account credential version and signed JWT expiry. The API records only the WebSocket session id, normalized account email, `auth_version` and expiry instant; it does not retain the raw access JWT. A stale or expired JWT cannot establish a new session.

That check is not limited to the initial handshake. Every later inbound STOMP frame must still belong to an unexpired registered session/principal pair and the account must still be `ACTIVE` with the same `auth_version`. Password change/reset, account suspension or token expiry therefore rejects further activity from an already-established session. Disconnect events remove the in-memory binding.

Existing subscriptions are protected independently on the client outbound channel. Immediately before a broker `MESSAGE` is delivered to a WebSocket session, `WebSocketOutboundSecurityInterceptor` requires an unexpired binding, reloads the account and requires the session's stored credential version to remain current and the account to remain `ACTIVE`. Missing, expired, suspended or stale session identities are dropped fail-closed before chat, notification or live-tracking data reaches the client, and their registry entry is removed. This does not claim to forcibly tear down the underlying TCP/WebSocket connection at the exact instant of a credential change; it prevents further protected realtime delivery and rejects subsequent inbound frames.

The session registry is process-local and bounded by live WebSocket sessions rather than arbitrary caller keys. It is cleaned on normal/disconnected session events and when an expired or stale outbound or inbound session is detected. In a future multi-instance deployment, each WebSocket connection remains owned and validated by the API instance serving that connection; account state itself is reloaded from the shared database for each protected delivery.

## Inbound message limiter

The inbound channel runs interceptors in this order:

1. `WebSocketSecurityInterceptor` authenticates and credential-binds `CONNECT` frames, revalidates established sessions, authorizes protected subscriptions, and rejects client `SEND` frames.
2. `WebSocketInboundRateLimitInterceptor` limits subsequent authenticated STOMP frames by trusted principal.

The client outbound channel independently runs `WebSocketOutboundSecurityInterceptor` before client `MESSAGE` delivery so an existing subscription cannot outlive account suspension or credential invalidation.

The default production budget is 120 inbound frames per 10 seconds per authenticated account. The limit is shared across that account's concurrent WebSocket sessions on a single API instance, so opening additional sockets does not bypass the application-level budget. `CONNECT` and `DISCONNECT` frames are not charged; authentication and authorization still run normally.

When the budget is exceeded the inbound frame is rejected before application message handlers execute. The limiter uses bounded in-memory state and fails closed for new principals if its configured entry capacity is exhausted. Expired windows are cleaned incrementally.

Production knobs:

- `WEBSOCKET_INBOUND_RATE_LIMIT_MAX_MESSAGES` (default `120`, allowed `1..10000`)
- `WEBSOCKET_INBOUND_RATE_LIMIT_WINDOW_SECONDS` (default `10`, allowed `1..3600`)
- `WEBSOCKET_INBOUND_RATE_LIMIT_MAX_ENTRIES` (default `10000`, allowed `100..1000000`)

## Deployment boundary

Gateway connection/handshake limits and the API message limiter protect different stages and are both required. A multi-instance deployment should enforce equivalent connection/request limits at the outermost trusted ingress and should use shared or ingress-level application abuse controls where a per-instance budget could otherwise be multiplied across replicas.

Origin restrictions and JWT-based WebSocket authentication remain mandatory and independent of these limits. The connection caps are abuse controls, not user/session authorization decisions.
