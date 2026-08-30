# Production deployment contract

## Scope

`infra/compose/compose.prod.yaml` is the repository's single-host production baseline. It keeps the API, web gateway and PostgreSQL/PostGIS configuration aligned with the current application contract. It is not a complete high-availability platform: TLS termination, managed backups, disaster recovery, external object storage and multi-node orchestration remain separate operational work.

The production Compose file intentionally fails closed for secrets and commercial settings that must never inherit local-development defaults.

## Required production configuration

The deployment environment must provide at least:

- `DB_NAME`, `DB_USER`, `DB_PASSWORD`;
- `JWT_SECRET`;
- `WEBSOCKET_ALLOWED_ORIGIN_PATTERNS` with trusted HTTPS origins only;
- `STRIPE_SECRET_KEY` and `STRIPE_WEBHOOK_SECRET`;
- `PLATFORM_FEE_BASIS_POINTS` as an explicit commercial decision;
- `GOOGLE_MAPS_ROUTES_API_KEY` when the default production routing provider is Google;
- `ATTACHMENT_ENCRYPTION_KEY_BASE64`, generated from 32 random bytes and stored as a server-side secret;
- `VITE_STRIPE_PUBLISHABLE_KEY`;
- `VITE_GOOGLE_MAPS_BROWSER_KEY` and `VITE_GOOGLE_MAPS_MAP_ID`.

Google/Apple authentication variables are optional only when the corresponding sign-in method is intentionally unavailable.

Never reuse `.env.example` as a production secret file. It contains local/test placeholders and development-oriented values.

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

Do not expose a real customer deployment over plain HTTP. Stripe return URLs, Apple authentication redirects, Stripe Connect onboarding URLs and WebSocket origins must use the production HTTPS origin.

## CI contract

`.github/scripts/production-compose-contract-smoke.sh` renders the production Compose file with deterministic placeholder settings and verifies that:

- current finance, payout, submitted-payout reconciliation and tracking settings reach the API container;
- sandbox payouts stay disabled;
- attachment encryption configuration is present;
- encrypted attachment storage is backed by a persistent volume;
- API/web health checks and health-based startup ordering remain present;
- omitting the production attachment encryption key causes Compose configuration to fail.

The main CI workflow gates its container runtime smoke on this production-configuration contract so future application settings cannot silently drift away from `compose.prod.yaml`.
