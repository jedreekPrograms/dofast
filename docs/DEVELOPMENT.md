# Development guide

## Prerequisites

- Docker Desktop / Docker Engine with Compose v2
- Java 21 when running the API from the IDE
- Node.js 24 LTS when running the web client outside Docker

## Full stack

Copy `.env.example` to `.env`, then run:

```bash
docker compose up --build
```

Windows users can run `./scripts/dev-up.ps1`; Linux/macOS users can run `./scripts/dev-up.sh`.

## Backend from IDE

Start only the database:

```bash
docker compose up -d db
```

Then run the Spring Boot application from `apps/api`. Use the `local` Spring profile and configure datasource/secret environment variables from `.env`.

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
npm --prefix apps/web run lint
npm --prefix apps/web run build
```

CI repeats the relevant checks on every push and pull request.

## Database changes

Never rely on Hibernate `ddl-auto=update` for shared or production environments. Every schema change must be added as a new immutable Flyway migration under `apps/api/src/main/resources/db/migration`.
