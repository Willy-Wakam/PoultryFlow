# PoultryFlow architecture

## Architectural style

PoultryFlow uses a modular monolith for the MVP. A single deployable Spring Boot application keeps development, testing, operations, transactions, and releases manageable for the initial team while explicit module boundaries prevent the codebase from becoming one undifferentiated application.

The web client communicates with the backend through REST APIs. Microservices are intentionally not used for the MVP: the product does not yet need independent service deployment, distributed transactions, or the operational cost of a distributed system.

## Backend module boundaries

The package below each module name is its ownership boundary. US-001 establishes only package markers; entities, repositories, controllers, and services will be introduced by their corresponding stories.

| Module | Owns |
| --- | --- |
| `farm` | Farm structure, poultry houses, and farm-level configuration |
| `identity` | Identity references, access policies, and authorization boundaries |
| `flock` | Flock lifecycle, placement, movement, and production records |
| `inventory` | Stock items, movement-based balances, and adjustments |
| `finance` | Expenses, cost classification, and financial summaries |
| `sales` | Customers, sales, payments, and sales records |
| `approval` | Approval requests, decisions, and approval-required policies |
| `audit` | Immutable audit events and traceability views |
| `reporting` | Read models, operational summaries, dashboards, and reports |
| `synchronization` | Offline commands, idempotency, and conflict handling |
| `document` | Document metadata and access to object-stored files |

## Module interaction rules

- A module owns its domain model and persistence internals.
- Other modules must not directly access that module's repositories, database tables, or internal implementation packages.
- Cross-module behavior should use explicit application or domain boundaries, such as published interfaces, commands, queries, or domain events.
- REST controllers belong at application entry boundaries and should delegate business behavior to the owning module.
- A shared area may contain genuinely cross-cutting primitives, but generic helpers should not accumulate in a broad `utils` package.
- Module boundaries should remain testable so that a future architecture test can enforce them when production code is introduced.

## API contract boundary

Business REST endpoints use the `/api/v1` major-version prefix for the MVP. Additive compatible changes remain within v1; breaking changes must be documented and normally move to a new major prefix.

Cross-cutting API metadata, reusable OpenAPI components, and common ProblemDetail handling live under `com.poultryflow.shared.api`. Business controllers do not move into that shared area: each controller remains owned by its corresponding module and delegates to that module's application boundary.

Infrastructure and documentation endpoints such as `/actuator/health`, `/v3/api-docs`, and `/swagger-ui/` remain outside the business API prefix. See [api-contract.md](api-contract.md) for versioning, errors, idempotency, and contract-evolution rules.

## Authentication boundary

Authentication spans the frontend application boundary and the backend `identity.security` package. The React application delegates browser login to Keycloak with Authorization Code Flow and PKCE S256. The Spring Boot application is an OAuth2 Resource Server that validates bearer access tokens for `/api/v1/**`; it is not an OAuth2 login client and has no backend client secret.

Cross-cutting security owns token validation, fail-closed routing, allowlisted `poultryflow-api` client-role mapping, and safe 401/403 responses. Authorization policies remain close to controller and use-case boundaries through method security. Frontend role guards are a user-experience aid only; the backend remains authoritative.

The current roles are coarse application permissions. A future `FarmMembership` model must remain authoritative for access to a specific farm, so a global role can never grant access to a farm for which the user has no valid membership. Persistent authorization-denied audit events remain owned by the audit module. See [authentication.md](authentication.md) for the detailed claim and local configuration.

## Frontend organization

`src/app` owns application composition and global presentation. `src/auth` owns the Keycloak adapter, typed React authentication state, and sign-in controls. Reusable technical UI belongs in `src/components`. Business behavior will be grouped under `src/features` when feature stories begin, without pre-creating empty implementations.

## Deferred architecture

Docker Compose defines the local PostgreSQL and Keycloak services, Keycloak/OIDC authentication and coarse role-based authorization are implemented, and GitHub Actions enforces repository quality gates. FarmMembership authorization, credential recovery, advanced session lifecycle, persistent audit storage, application datasource wiring, JPA persistence, Flyway business migrations, S3-compatible storage, PWA capabilities, IndexedDB/Dexie, and idempotent synchronization remain deferred to their owning stories. IoT, message brokers, Kubernetes, native mobile applications, and AI functionality are outside the MVP architecture.
