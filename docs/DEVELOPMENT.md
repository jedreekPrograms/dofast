# Development guide

## Prerequisites

- Docker Desktop / Docker Engine with Compose v2
- Java 21 when running the API from the IDE
- Node.js 24 LTS when running the web client outside Docker

PostgreSQL runs in Docker for the normal development workflow, so a host PostgreSQL installation is not required.

## Full stack

Copy `.env.example` to `.env`, then run:

```bash
docker compose up --build
```

Windows users can run `./scripts/dev-up.ps1`; Linux/macOS users can run `./scripts/dev-up.sh`.

The local PostgreSQL endpoint is `localhost:5434`; containers communicate with it as `db:5432`.

## Backend from IDE

Start only PostgreSQL:

```bash
docker compose up -d db
```

Then run the Spring Boot application from `apps/api`. Use the `local` Spring profile and configure datasource/secret environment variables from `.env` when overriding defaults.

## Web from host

```bash
cd apps/web
npm ci
npm run dev
```

## Validation before commit

```bash
mvn -B -f apps/api/pom.xml verify
npm --prefix apps/web ci
npm --prefix apps/web audit --omit=dev --audit-level=high
npm --prefix apps/web run lint
npm --prefix apps/web run build
docker compose build api web
```

CI repeats these checks and also performs a PostgreSQL + Flyway + API runtime smoke test on every pull request.

## Database changes

Never rely on Hibernate `ddl-auto=update` for shared or production environments. Every schema change must be added as a new immutable Flyway migration under `apps/api/src/main/resources/db/migration`.

The existing `V1` migration is the clean PostgreSQL baseline. Once a migration has been released to a shared environment, do not edit it—add `V2`, `V3`, and so on instead.
