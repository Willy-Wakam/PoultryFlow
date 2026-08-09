# PoultryFlow

PoultryFlow is a web-based poultry farm management platform for a farm in Cameroon. It is intended to support secure remote operations from Cameroon, Germany, and Canada while keeping farm records consistent and traceable.

## Project status

PoultryFlow is in its MVP foundation phase. The repository contains runnable backend and frontend skeletons, documented modular-monolith boundaries, and a Docker Compose local development stack with PostgreSQL and Keycloak. Authentication integration, business features, offline synchronization, and production deployment automation have not been implemented yet.

IoT integrations are explicitly out of scope.

## Architecture overview

PoultryFlow is a monorepo containing a React web application and a Spring Boot REST API. The backend is organized as a modular monolith: business capabilities have explicit package boundaries but are built and deployed as one application for the MVP.

The frontend uses the backend Actuator health endpoint as a development smoke check. Vite proxies `/actuator` requests to Spring Boot, so no application-wide CORS policy is needed for this initial local workflow.

See [docs/architecture.md](docs/architecture.md) for module ownership and dependency rules and [infrastructure/README.md](infrastructure/README.md) for local service details.

## Technology stack

- Frontend: React, TypeScript, Vite
- Backend: Java 21, Spring Boot, Maven, Spring Web, Bean Validation, Actuator
- Local infrastructure: Docker Compose, PostgreSQL 18.4, Keycloak 26.7.0
- Architecture: modular monolith and REST APIs
- Planned infrastructure: Flyway, S3-compatible object storage, GitHub Actions
- Planned offline support: PWA, IndexedDB/Dexie, idempotent synchronization

## Repository structure

```text
PoultryFlow/
├── backend/          Spring Boot application and module packages
├── frontend/         React and Vite web application
├── infrastructure/   Local infrastructure scripts and documentation
├── docs/             Architecture documentation
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
- Node.js 22.12 or later
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

With the local infrastructure running:

```bash
cd backend
mvn spring-boot:run
```

The API starts on `http://localhost:8080`. Verify it with:

```bash
curl http://localhost:8080/actuator/health
```

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
docker compose config
docker compose ps
```

Backend tests and build:

```bash
cd backend
mvn test
mvn verify
```

Frontend type checking and production build:

```bash
cd frontend
npm install
npm run typecheck
npm run build
```

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
