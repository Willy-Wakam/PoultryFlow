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

## Frontend organization

`src/app` owns application composition and global presentation. Reusable technical UI belongs in `src/components`. Business behavior will be grouped under `src/features` when feature stories begin, without pre-creating empty implementations.

## Deferred architecture

PostgreSQL, Flyway, Keycloak, S3-compatible storage, Docker Compose, GitHub Actions, PWA capabilities, IndexedDB/Dexie, and idempotent synchronization are planned but not implemented by US-001. IoT, message brokers, Kubernetes, native mobile applications, and AI functionality are outside the MVP architecture.
