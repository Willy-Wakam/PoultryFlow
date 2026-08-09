# PoultryFlow

PoultryFlow is a web-based poultry farm management platform for a farm in Cameroon. It is intended to support secure remote operations from Cameroon, Germany, and Canada while keeping farm records consistent and traceable.

## Project status

PoultryFlow is in its MVP foundation phase. The repository contains runnable backend and frontend skeletons, documented modular-monolith boundaries, and a Docker Compose local development stack with PostgreSQL and Keycloak. Authentication integration, business features, offline synchronization, and production deployment automation have not been implemented yet.

IoT integrations are explicitly out of scope.

## Architecture overview

PoultryFlow is a monorepo containing a React web application and a Spring Boot REST API. The backend is organized as a modular monolith: business capabilities have explicit package boundaries but are built and deployed as one application for the MVP.

The frontend uses the backend Actuator health endpoint as a development smoke check. Vite proxies `/actuator` requests to Spring Boot, so no application-wide CORS policy is needed for this initial local workflow.

See [docs/architecture.md](docs/architecture.md) for module ownership and dependency rules, [docs/api-contract.md](docs/api-contract.md) for REST contract conventions, and [infrastructure/README.md](infrastructure/README.md) for local service details.

## Technology stack

- Frontend: React, TypeScript, Vite
- Backend: Java 21, Spring Boot, Maven, Spring Web, Bean Validation, Actuator, Springdoc/OpenAPI
- Local infrastructure: Docker Compose, PostgreSQL 18.4, Keycloak 26.7.0
- CI: GitHub Actions quality gates for backend, frontend, and infrastructure
- Architecture: modular monolith and REST APIs
- Planned infrastructure: Flyway, S3-compatible object storage
- Planned offline support: PWA, IndexedDB/Dexie, idempotent synchronization

## Repository structure

```text
PoultryFlow/
├── backend/          Spring Boot application and module packages
├── frontend/         React and Vite web application
├── infrastructure/   Local infrastructure scripts and documentation
├── docs/             Architecture documentation
├── .github/workflows Automated quality gates
├── compose.yaml      PostgreSQL and Keycloak local services
├── .env.example      Development-only environment template
├── .editorconfig
├── .gitignore
└── README.md
```

## Prerequisites

- Docker Engine or Docker Desktop with Docker Compose v2
- JDK 21
- Maven 3.9 or later
- Node.js 22.13 or later
- npm 10 or later

## Initial setup

Create the ignored local environment file:

```bash
cp .env.example .env
```

The committed values are safe examples for local development only. Do not reuse them in production.

Start and inspect PostgreSQL and Keycloak:

```bash
docker compose up -d
docker compose ps
```

Local ports are:

| Component | URL or port |
| --- | --- |
| React/Vite | `http://localhost:5173` |
| Spring Boot | `http://localhost:8080` |
| Keycloak | `http://localhost:8081` |
| Keycloak health | `http://localhost:9001/health/ready` |
| PostgreSQL | `localhost:5432` |

The host ports for PostgreSQL and Keycloak can be changed in `.env`. The current backend does not connect to either service yet; the Compose stack prepares those dependencies for later stories without adding persistence or authentication behavior.

## Run the backend

The backend currently starts independently of PostgreSQL and Keycloak:

```bash
cd backend
mvn spring-boot:run
```

The API starts on `http://localhost:8080`. Verify it with:

```bash
curl http://localhost:8080/actuator/health
```

## API documentation

With the backend running, access:

- Generated OpenAPI JSON: `http://localhost:8080/v3/api-docs`
- Swagger UI: `http://localhost:8080/swagger-ui.html`

MVP business endpoints use the `/api/v1` prefix. See [docs/api-contract.md](docs/api-contract.md) for versioning, ProblemDetail errors, the `Idempotency-Key` convention, and contract-evolution rules.

## Run the frontend

Start the backend first, then use a second terminal:

```bash
cd frontend
npm install
npm run dev
```

Open `http://localhost:5173`. The page displays the backend health reported through the Vite development proxy. If the backend is stopped, the page reports that it is unavailable without failing to render.

## Stop local infrastructure

Normal shutdown keeps PostgreSQL and Keycloak data in the Docker volume:

```bash
docker compose down
```

To start the same persisted environment again:

```bash
docker compose up -d
```

`docker compose down --volumes` is a destructive reset that permanently removes both local databases. It is not part of normal shutdown. See [infrastructure/README.md](infrastructure/README.md) before using it.

## Run checks

Validate the local infrastructure configuration:

```bash
docker compose --env-file .env.example config --quiet
bash -n infrastructure/postgres/init/01-create-keycloak-database.sh
```

Backend tests and build:

```bash
cd backend
mvn -B verify
```

`mvn -B verify` is the full backend quality gate and covers compilation, test compilation, tests, packaging, and verification. Use `mvn -B test` when you only need to run through the test phase locally.

Frontend dependency, type, lint, formatting, and production build checks:

```bash
cd frontend
npm ci
npm run typecheck
npm run lint
npm run format:check
npm run build
```

Use `npm run format` from `frontend/` to apply the configured frontend formatting rules.

## CI quality gates

GitHub Actions runs on pull requests targeting `dev` or `main` and on direct pushes to those branches. The workflows expose these checks:

- `Backend CI / Backend quality gates`: Java 21 compilation, tests, and Maven verification.
- `Frontend CI / Frontend quality gates`: deterministic dependency installation, TypeScript checking, ESLint, Prettier verification, and the Vite production build on Node.js 22.
- `Infrastructure CI / Infrastructure quality gates`: static Docker Compose validation and shell syntax validation.

The active repository ruleset requires changes to `dev` and `main` to arrive through a pull request. All three quality gates must pass against an up-to-date target branch before merge; no human approval is required.

## Development and production

`compose.yaml` and `.env.example` define a development profile only. Keycloak runs with `start-dev`, sample credentials are intentionally weak, and ports are exposed to the workstation. Production requires separate deployment configuration, managed secrets, TLS, backups, and hardened network access; no production credentials or deployment stack belong in this repository foundation.

## Branch strategy

```text
main
└── dev
    └── feat/us-XXX-description
```

- `main` contains production or release-ready integration points.
- `dev` is the shared development integration branch.
- `feat/*` branches are created from the latest `dev` for individual backlog stories.
- Feature branches return to `dev` through pull requests; they are not merged directly.

## Contribution workflow

1. Select an issue from the [GitHub backlog](https://github.com/Willy-Wakam/PoultryFlow/issues).
2. Update local remote references and create a focused `feat/us-XXX-description` branch from `origin/dev`.
3. Keep changes within the issue scope and add proportionate tests and documentation.
4. Run the backend and frontend checks relevant to the change.
5. Commit with a conventional commit message and push the feature branch.
6. Open a pull request targeting `dev` and reference the issue in its description.
