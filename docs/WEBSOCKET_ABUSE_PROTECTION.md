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

## Inbound message limiter

The inbound channel runs interceptors in this order:

1. `WebSocketSecurityInterceptor` authenticates `CONNECT` frames and authorizes protected subscriptions.
2. `WebSocketInboundRateLimitInterceptor` limits subsequent authenticated STOMP frames by trusted principal.

The default production budget is 120 inbound frames per 10 seconds per authenticated account. The limit is shared across that account's concurrent WebSocket sessions on a single API instance, so opening additional sockets does not bypass the application-level budget. `CONNECT` and `DISCONNECT` frames are not charged; authentication and authorization still run normally.

When the budget is exceeded the inbound frame is rejected before application message handlers execute. The limiter uses bounded in-memory state and fails closed for new principals if its configured entry capacity is exhausted. Expired windows are cleaned incrementally.

Production knobs:

- `WEBSOCKET_INBOUND_RATE_LIMIT_MAX_MESSAGES` (default `120`, allowed `1..10000`)
- `WEBSOCKET_INBOUND_RATE_LIMIT_WINDOW_SECONDS` (default `10`, allowed `1..3600`)
- `WEBSOCKET_INBOUND_RATE_LIMIT_MAX_ENTRIES` (default `10000`, allowed `100..1000000`)

## Deployment boundary

Gateway connection/handshake limits and the API message limiter protect different stages and are both required. A multi-instance deployment should enforce equivalent connection/request limits at the outermost trusted ingress and should use shared or ingress-level application abuse controls where a per-instance budget could otherwise be multiplied across replicas.

Origin restrictions and JWT-based WebSocket authentication remain mandatory and independent of these limits. The connection caps are abuse controls, not user/session authorization decisions.
