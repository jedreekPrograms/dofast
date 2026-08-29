# doFast

**doFast** is a local peer-to-peer task marketplace for getting everyday jobs done quickly and safely. A requester publishes a paid task, another user accepts it, funds are protected while the task is in progress, and payment is released after completion.

The repository is organized as a production-oriented monorepo so the API, web client, infrastructure and operational documentation evolve together.

## Repository layout

```text
.
├── apps/
│   ├── api/                 # Spring Boot API
│   └── web/                 # React + Vite web client
├── infra/
│   ├── compose/             # production-oriented Compose definition
│   └── nginx/               # web gateway configuration
├── docs/                    # architecture, development and security docs
├── scripts/                 # developer convenience scripts
├── .github/                 # CI configuration
├── compose.yaml             # local full-stack environment
└── .env.example             # documented local configuration contract
```

## Local quick start

Requirements: Docker Desktop / Docker Engine with Compose v2.

```bash
cp .env.example .env
docker compose up --build
```

Windows PowerShell:

```powershell
Copy-Item .env.example .env
docker compose up --build
```

Services:

- Web: `http://localhost:5173`
- API: `http://localhost:8080`
- API health: `http://localhost:8080/actuator/health`
- PostgreSQL/PostGIS: `localhost:5434`

For IDE-based backend development you can start only the database with `docker compose up -d db` and run the API from `apps/api`.

## Technology baseline

- Java 21 LTS
- Spring Boot 4.1.x
- React 19 + Vite
- PostgreSQL 18.6 + PostGIS
- Flyway database migrations
- Docker / Docker Compose
- Nginx
- GitHub Actions

## Why PostgreSQL + PostGIS

The core doFast workflow is transactional and money-sensitive, so the primary datastore is PostgreSQL rather than a document database. PostgreSQL gives us strong transaction semantics, mature locking/concurrency tools and rich indexing, while PostGIS provides indexed geographic matching for nearby tasks without moving distance calculations into application memory.

## Documentation

- [Architecture](docs/ARCHITECTURE.md)
- [Accounts and access](docs/ACCOUNTS_AND_ACCESS.md)
- [Job lifecycle](docs/JOB_LIFECYCLE.md)
- [Job discovery](docs/JOB_DISCOVERY.md)
- [Location and nearby matching](docs/LOCATION_AND_MATCHING.md)
- [Production deployment](docs/PRODUCTION_DEPLOYMENT.md)
- [Development](docs/DEVELOPMENT.md)
- [Security baseline](docs/SECURITY.md)

## Project status

The repository is being developed on the **Carlisle** technical baseline. Core marketplace concepts already exist, but the product is still under active development and should not yet be treated as production-ready for real customer funds.
