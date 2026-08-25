# doFast architecture

## Goal

doFast is a local two-sided marketplace. The architecture must support a transactional workflow (publish → accept → execute → confirm → settle), realtime communication, location-based discovery, trust workflows and moderation without coupling every feature into one large service layer.

## Current architectural style

The backend is a **modular monolith**. This is intentional: it keeps transactions and development simple while domain boundaries remain explicit enough to extract services later only if scale or team ownership requires it.

Current domains:

- `user` — accounts, authenticated profile management and public profile composition;
- `job` — task lifecycle and ownership;
- `location` — geospatial task location, privacy rules and nearby discovery;
- `wallet` — internal balance and immutable financial ledger entries;
- `payment` — Stripe funding, escrow transitions and financial reconciliation;
- `chat` — realtime task communication;
- `notification` — persisted and realtime user notifications;
- `review` — bilateral reputation after completed work;
- `dispute` — dispute lifecycle, evidence access and administrator resolution;
- `verification` — identity-verification state, provider boundary, audit trail and public trust signal;
- `common` / `config` — cross-cutting infrastructure only.

`matching` remains a future domain if discovery evolves beyond the current PostGIS nearby-query model. Broader moderation capabilities should remain explicit admin/dispute modules rather than becoming a generic `common` dumping ground.

## Repository boundaries

- `apps/api` owns backend application code and its build definition.
- `apps/web` owns the customer-facing web client and administrator UI.
- `infra` owns runtime gateway/Compose configuration and must not contain business logic.
- `docs` contains architectural decisions and operating rules.
- `scripts` contains thin developer wrappers only; application behavior must not depend on scripts.

## Runtime topology

```text
Browser
   |
   v
Nginx / Web container
   |  \
   |   \-- static React SPA
   |
   +---- /api, /ws ----> Spring Boot API ----> PostgreSQL + PostGIS
                              |
                              +---------------> Stripe
                              |
                              +---------------> verification provider (adapter boundary)
```

## Data platform

PostgreSQL is the system-of-record database for transactional product data. PostGIS is enabled for geospatial discovery and exact-location access is controlled at the application boundary. `pg_trgm` supports indexed text discovery. Flyway is the sole owner of schema evolution; Hibernate must not auto-update production schema.

Financial state is represented by wallet balances plus an auditable wallet ledger and escrow/payment records. Reconciliation checks are used to detect divergence rather than silently treating one derived representation as correct.

Identity verification follows data minimization: doFast stores verification state and opaque provider metadata, not copies or numbers of identity documents.

## Architectural rules

1. Controllers translate transport concerns; they do not contain business decisions.
2. Domain services own use cases and transaction boundaries.
3. Repositories are accessed by the owning domain service; cross-domain writes should happen through services, not another domain's repository.
4. DTOs are API contracts; JPA entities are not returned directly.
5. External providers (Stripe, verification, future delivery channels/maps) must be hidden behind application-facing services/adapters.
6. Database schema changes are versioned with Flyway. Hibernate schema auto-update is not permitted in production.
7. Secrets are supplied through the environment/secret manager and are never committed.
8. New infrastructure must expose health signals and have deterministic configuration.
9. Start as a modular monolith. Split into services only for a measured operational reason.
10. PostgreSQL is the authoritative transactional datastore; caches/search indexes may be introduced later but must not silently become the source of truth for money, job state or trust decisions.
11. Sensitive data must be minimized. Public DTOs expose only the least information required for the product feature.
12. State-changing operations that can race must use database constraints plus an explicit locking/idempotency strategy.

## Future scale path

The likely extraction boundaries, if ever needed, are notification delivery, realtime messaging, search/matching, provider webhook processing and asynchronous payment processing. The core job/escrow consistency boundary should remain strongly transactional for as long as practical. Verification provider integrations can be replaced behind the provider adapter without changing the public trust contract.
