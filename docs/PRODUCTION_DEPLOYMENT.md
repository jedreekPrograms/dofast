# Production deployment contract

## Scope

`infra/compose/compose.prod.yaml` is the repository's single-host production baseline. It keeps the API, web gateway and PostgreSQL/PostGIS configuration aligned with the current application contract. It is not a complete high-availability platform: TLS termination, managed backups, disaster recovery, external object storage and multi-node orchestration remain separate operational work.

The production Compose file intentionally fails closed for secrets and commercial settings that must never inherit local-development defaults.

## Required production configuration

The deployment environment must provide at least:

- `DB_NAME`, `DB_USER`, `DB_PASSWORD`;
- `JWT_SECRET`;
- `WEBSOCKET_ALLOWED_ORIGIN_PATTERNS` with trusted HTTPS origins only;
- `SMTP_HOST`, `SMTP_USERNAME`, `SMTP_PASSWORD` for password recovery;
- `PASSWORD_RESET_BASE_URL` pointing to the trusted HTTPS `/reset-password` page;
- `PASSWORD_RECOVERY_FROM_ADDRESS` using an approved sender identity;
- `STRIPE_SECRET_KEY` and `STRIPE_WEBHOOK_SECRET`;
- `PLATFORM_FEE_BASIS_POINTS` as an explicit commercial decision;
- `GOOGLE_MAPS_ROUTES_API_KEY` when the default production routing provider is Google;
- `ATTACHMENT_ENCRYPTION_KEY_BASE64`, generated from 32 random bytes and stored as a server-side secret;
- `VITE_STRIPE_PUBLISHABLE_KEY`;
- `VITE_GOOGLE_MAPS_BROWSER_KEY` and `VITE_GOOGLE_MAPS_MAP_ID`.

Google/Apple authentication variables are optional only when the corresponding sign-in method is intentionally unavailable.

Never reuse `.env.example` as a production secret file. It contains local/test placeholders and development-oriented values.

## Authentication and browser-session configuration

Production uses short-lived Bearer access tokens plus rotating opaque browser refresh sessions. Access JWTs default to 10 minutes and the application rejects configured lifetimes above 15 minutes. The web client keeps the access JWT only in memory, not in browser Web Storage.

The production Spring profile hard-wires refresh cookies as `Secure=true`. `infra/compose/compose.prod.yaml` intentionally does **not** expose an `AUTH_COOKIE_SECURE` override, so an operator cannot accidentally downgrade the HttpOnly refresh cookie to plaintext HTTP while still using the production profile.

The production Compose file forwards these tunable session controls:

- `JWT_EXPIRATION_MS` — defaults to `600000` (10 minutes);
- `AUTH_REFRESH_TTL_DAYS` — refresh-session lifetime, default 30 days;
- `AUTH_REFRESH_REUSE_GRACE_SECONDS` — near-simultaneous rotation grace, default 15 seconds;
- `AUTH_SESSION_RETENTION_DAYS` — expired/revoked session audit retention, default 7 days;
- `AUTH_SESSION_CLEANUP_INTERVAL_MS` — cleanup scheduler cadence;
- `AUTH_COOKIE_SAME_SITE` — `Strict` by default; the application accepts only `Strict` or `Lax`.

The production web gateway is expected to keep the browser and `/api` on the same site/origin. The default `SameSite=Strict` model is deliberately incompatible with casually moving the API to an unrelated cross-site origin.

Only SHA-256 hashes of refresh and CSRF secrets are stored in PostgreSQL. Successful refresh rotates both secrets. Password changes and password resets increment `users.auth_version` and revoke every active refresh session for the user. Access JWTs carry the signed credential version in `av`; the JWT filter reloads the user and rejects stale-version tokens immediately rather than waiting for their normal expiry. Replay of an already rotated refresh credential outside the configured grace revokes the active session family.

## Password recovery delivery

Production hard-wires `dofast.security.password-recovery.delivery=smtp`. Local/CI may disable outbound delivery, but the production profile cannot silently fall back to a no-op provider.

SMTP uses authenticated STARTTLS and requires TLS negotiation. Production Compose forwards:

- `SMTP_HOST`;
- `SMTP_PORT`, default `587`;
- `SMTP_USERNAME`;
- `SMTP_PASSWORD`;
- `PASSWORD_RESET_BASE_URL`;
- `PASSWORD_RECOVERY_FROM_ADDRESS`;
- `PASSWORD_RESET_TTL_MINUTES`, default `30`;
- `PASSWORD_RESET_RETENTION_DAYS`, default `7`;
- `PASSWORD_RESET_CLEANUP_INTERVAL_MS`.

`PASSWORD_RESET_BASE_URL` must be an absolute HTTPS URL without a query or fragment. The API appends the URL-encoded opaque reset token itself.

The public forgot-password request does not wait for SMTP. It commits the hash-only reset credential first, then an asynchronous `AFTER_COMMIT` listener performs delivery from an in-memory event. This prevents mail-server latency from becoming an obvious account-existence timing signal and avoids persisting raw reset credentials in an outbox.

SMTP delivery failures log only the internal user id. A retrying user automatically invalidates older active reset credentials before a fresh one is generated. Full lifecycle details are in `docs/PASSWORD_RECOVERY.md`.

## Finance and payout configuration

The production Compose file forwards the current platform-fee, payout dispatcher and submitted-payout reconciliation settings instead of relying on hidden application defaults.

Worker payout remains fail-closed:

- `PAYOUT_PROVIDER` defaults to `disabled`;
- `PAYOUT_SANDBOX_ENABLED` is hard-disabled in the production Compose file and cannot be enabled by a host environment variable;
- Stripe Connect recipient onboarding defaults to disabled;
- Stripe Connect live dispatch defaults to disabled and requires its independent kill switch;
- Stripe Connect submitted-payout reconciliation defaults to disabled and requires its own independent kill switch.

`PAYOUT_STRIPE_CONNECT_RECONCILIATION_ENABLED=true` does **not** enable new money movement. The reconciler only retrieves an already-created Stripe connected-account Payout by the durable `provider_reference` stored on a local `SUBMITTED` request. It never creates a new platform Transfer or connected-account Payout.

This separation allows operators to disable new Stripe Connect dispatch while still resolving payouts that were already accepted by Stripe. `PAYOUT_SUBMITTED_RECONCILIATION_SECONDS` controls the grace period/backoff between provider reads, while `PAYOUT_STRIPE_CONNECT_RECONCILIATION_INTERVAL_MS` controls the scheduler cadence.

Signed webhook settlement remains the preferred fast path. Reconciliation is a missed/delayed-webhook safety net and reuses the same terminal settlement and transfer-reversal logic. Provider read failures, unknown statuses or identity mismatches do not release reserved wallet funds and do not return a submitted payout to the dispatch queue.

Enabling Stripe Connect money movement is an explicit operational action. Existing signed webhook settlement and, when explicitly enabled, read-only reconciliation remain available for payouts already submitted before the dispatch kill switch is turned off.

## Persistent data

The single-host baseline persists two independent Docker volumes:

- `postgres_data` for PostgreSQL/PostGIS;
- `attachment_data` mounted at `/var/lib/dofast/attachments` for encrypted job attachment objects.

The API production profile requires both an explicit attachment storage root and an explicit attachment encryption key. It can no longer silently inherit the repository's local/CI encryption key.

The encryption key must remain available for as long as encrypted objects need to be readable. Losing the key makes persisted attachment ciphertext unrecoverable. Automated key rotation is not implemented yet and must not be simulated by simply replacing the environment variable.

A Docker volume is persistence, not a backup. Off-host database/attachment backups, restore drills and a future S3-compatible object-storage adapter are separate launch requirements.

## Health and startup ordering

PostgreSQL, API and web services all expose health checks. The API waits for PostgreSQL health before startup, and the web container waits for API health rather than only for container creation.

This prevents a nominally running web container from being treated as ready while the backend is still unavailable.

## TLS boundary

The repository nginx container currently serves HTTP on its internal/public Compose port. A real deployment must terminate HTTPS in a trusted edge layer such as a cloud load balancer, ingress proxy or equivalent and forward the correct scheme/origin information.

Do not expose a real customer deployment over plain HTTP. Secure refresh cookies, password-reset links, Stripe return URLs, Apple authentication redirects, Stripe Connect onboarding URLs and WebSocket origins all depend on the production HTTPS boundary.

## CI contract

`.github/scripts/production-compose-contract-smoke.sh` renders the production Compose file with deterministic placeholder settings and verifies that:

- current auth-session, password-recovery, finance, payout, submitted-payout reconciliation and tracking settings reach the API container;
- the production access-token default stays at 10 minutes;
- the production Spring profile hard-wires `Secure` refresh cookies and Compose cannot override that invariant;
- production password recovery stays on SMTP with an explicit reset URL/sender;
- required SMTP/reset configuration fails closed when omitted;
- sandbox payouts stay disabled;
- attachment encryption configuration is present;
- encrypted attachment storage is backed by a persistent volume;
- API/web health checks and health-based startup ordering remain present;
- omitting the production attachment encryption key causes Compose configuration to fail.

The main CI runtime executes `.github/scripts/auth-session-smoke.sh` and `.github/scripts/password-recovery-smoke.sh` before the broader marketplace lifecycle smokes. Together they verify real login cookies, double-submit CSRF, refresh rotation, hash-only session/reset persistence, one-time reset consumption, immediate stale-Bearer invalidation and refresh-session revocation against Docker/PostgreSQL.

The main CI workflow gates its container runtime smoke on this production-configuration contract so future application settings cannot silently drift away from `compose.prod.yaml`.
