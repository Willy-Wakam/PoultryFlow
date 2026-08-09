# PoultryFlow API contract

## API style

PoultryFlow uses REST for the MVP. Business resources are represented as JSON over HTTP and are documented by the OpenAPI document generated from the running Spring Boot application.

## Base path

MVP business endpoints use the `/api/v1` prefix. Business controllers remain inside the module that owns the behavior; the shared API package contains only cross-cutting contract primitives.

Infrastructure and documentation endpoints do not use the business prefix:

- `/actuator/health`
- `/v3/api-docs`
- `/swagger-ui/`

## Versioning

The URI segment is the major API version. Additive, backward-compatible fields and operations may be introduced within v1. Breaking changes must be documented before implementation and normally require a new major prefix such as `/api/v2`.

Removing fields or operations, changing their meaning incompatibly, or silently changing validation and response semantics is not allowed within v1. Deprecations must identify a migration path and coexistence period before removal.

## Content types

- Normal JSON resources use `application/json`.
- Problem responses use `application/problem+json`.

## Error contract

Errors follow Spring's RFC 9457 `ProblemDetail` model rather than a separate proprietary envelope.

| Field | Meaning |
| --- | --- |
| `type` | URI reference identifying the problem category |
| `title` | Stable human-readable problem summary |
| `status` | HTTP status code |
| `detail` | Client-safe explanation for this occurrence |
| `instance` | URI reference identifying the request occurrence |
| `code` | Stable PoultryFlow machine-readable error code |
| `violations` | Optional structured validation failures |

Each validation violation contains `field` and `message`. Responses must never expose stack traces, Java exception names, database details, credentials, or other sensitive internals.

The initial runtime handler defines `VALIDATION_FAILED` and returns deterministic validation problems. Domain-specific error codes belong to future module stories.

## Idempotency

`Idempotency-Key` is the request-header convention for retryable non-idempotent writes:

- The client generates an opaque unique operation identifier, normally a UUID.
- A retry of the same logical operation reuses the same key.
- A key must never be reused for a different logical operation.
- Eligible POST and PATCH mutation endpoints may require the header.
- GET requests do not require it.
- Naturally idempotent operations do not automatically require it.
- A synchronized client operation may use its client-generated `operationId` UUID as the header value.

Future endpoint implementations must make the same key with the same logical request safe to retry and reproduce the original successful outcome instead of applying the operation twice. The same key with a different logical request must be rejected with HTTP 409 and the common ProblemDetail response.

US-004 defines this contract only. Request hashing, persistence, replay storage, locking, offline queues, and synchronization processing remain owned by later stories.

## Reusable OpenAPI components

The generated document publishes these reusable components for future module controllers:

- `ProblemDetail`: standard problem fields plus PoultryFlow extensions.
- `ValidationViolation`: structured invalid field and reason.
- `IdempotencyKey`: reusable `Idempotency-Key` header parameter.
- `ProblemResponse`: reusable `application/problem+json` response.

Future operations should reference these components rather than duplicate their definitions.

## OpenAPI publication

Start the backend without PostgreSQL or Keycloak:

```bash
cd backend
mvn spring-boot:run
```

The generated contract and documentation UI are available at:

- OpenAPI JSON: `http://localhost:8080/v3/api-docs`
- Swagger UI: `http://localhost:8080/swagger-ui.html`

The metadata title is `PoultryFlow API` and the API version is `v1`. No production server URL or placeholder business operation is added by this baseline.
