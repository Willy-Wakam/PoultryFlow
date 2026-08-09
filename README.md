# PoultryFlow

PoultryFlow is a web-based poultry farm management platform for a farm in Cameroon. It is intended to support secure remote operations from Cameroon, Germany, and Canada while keeping farm records consistent and traceable.

## Project status

PoultryFlow is in its MVP foundation phase. This repository currently contains runnable backend and frontend skeletons plus the documented modular-monolith boundaries. Database infrastructure, authentication, business features, offline synchronization, and deployment automation have not been implemented yet.

IoT integrations are explicitly out of scope.

## Architecture overview

PoultryFlow is a monorepo containing a React web application and a Spring Boot REST API. The backend is organized as a modular monolith: business capabilities have explicit package boundaries but are built and deployed as one application for the MVP.

The frontend uses the backend Actuator health endpoint as a development smoke check. Vite proxies `/actuator` requests to Spring Boot, so no application-wide CORS policy is needed for this initial local workflow.

See [docs/architecture.md](docs/architecture.md) for module ownership and dependency rules.

## Technology stack

- Frontend: React, TypeScript, Vite
- Backend: Java 21, Spring Boot, Maven, Spring Web, Bean Validation, Actuator
- Architecture: modular monolith and REST APIs
- Planned infrastructure: PostgreSQL, Keycloak, Flyway, S3-compatible object storage, Docker Compose, GitHub Actions
- Planned offline support: PWA, IndexedDB/Dexie, idempotent synchronization

The planned technologies are listed for architectural context; only the frontend and backend foundation is available in this story.

## Repository structure

```text
PoultryFlow/
├── backend/          Spring Boot application and module packages
├── frontend/         React and Vite web application
├── infrastructure/   Scope and ownership notes for future infrastructure
├── docs/             Architecture documentation
├── .editorconfig
├── .gitignore
└── README.md
```

## Prerequisites

- JDK 21
- Maven 3.9 or later
- Node.js 22.12 or later
- npm 10 or later

PostgreSQL and Keycloak are not required for the current foundation.

## Run the backend

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

## Run checks

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
