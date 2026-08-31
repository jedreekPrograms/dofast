# WebSocket abuse protection

doFast applies application-level abuse controls to authenticated STOMP traffic in addition to the existing origin and authorization policy.

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

This limiter protects authenticated application traffic on each API instance. It is deliberately not a replacement for transport-level connection and handshake limits. Production ingress/reverse proxy configuration must also cap WebSocket connection creation, concurrent connections per source, request/header sizes and idle timeouts. A multi-instance deployment should additionally enforce shared/ingress-level limits so traffic cannot multiply the per-instance budget by spreading across replicas.

Origin restrictions and JWT-based WebSocket authentication remain mandatory and independent of this limiter.
