# doFast architecture

## Goal

doFast is a local two-sided marketplace. The architecture must support a transactional workflow (publish → accept → execute → confirm → settle), realtime communication and future location-based matching without coupling every feature into one large service layer.

## Current architectural style

The backend is a **modular monolith**. This is intentional: it keeps transactions and development simple while domain boundaries remain explicit enough to extract services later only if scale or team ownership requires it.

Current domains:

- `user` — identity/profile-facing domain logic
- `job` — task lifecycle
- `wallet` — internal balance ledger
- `payment` — Stripe funding and escrow-related transaction orchestration
- `chat` — realtime task communication
- `review` — reputation
- `common` / `config` — cross-cutting infrastructure only

Planned domains should be introduced independently rather than added to `common`: `location`, `matching`, `notification`, `dispute`, `verification`, `moderation`.

## Repository boundaries

- `apps/api` owns backend application code and its build definition.
- `apps/web` owns the customer-facing web client.
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
   +---- /api, /ws ----> Spring Boot API ----> MariaDB
                              |
                              +---------------> Stripe
```

## Architectural rules

1. Controllers translate transport concerns; they do not contain business decisions.
2. Domain services own use cases and transaction boundaries.
3. Repositories are accessed by the owning domain service; cross-domain writes should happen through services, not another domain's repository.
4. DTOs are API contracts; JPA entities are not returned directly.
5. External providers (Stripe, notifications, maps) must be hidden behind application-facing services/adapters.
6. Database schema changes are versioned with Flyway. Hibernate schema auto-update is not permitted in production.
7. Secrets are supplied through the environment/secret manager and are never committed.
8. New infrastructure must expose health signals and have deterministic configuration.
9. Start as a modular monolith. Split into services only for a measured operational reason.

## Future scale path

The likely extraction boundaries, if ever needed, are notification delivery, realtime messaging, search/matching and asynchronous payment processing. The core job/escrow consistency boundary should remain strongly transactional for as long as practical.
