# Local development infrastructure

PoultryFlow uses Docker Compose to provide PostgreSQL and Keycloak for local development. This stack is intentionally small and does not represent production infrastructure.

## Services

| Service | Image | Purpose | Host ports |
| --- | --- | --- | --- |
| PostgreSQL | `postgres:18.4` | Persists the future PoultryFlow application database and Keycloak database | `5432` by default |
| Keycloak | `quay.io/keycloak/keycloak:26.7.0` | Runs the development identity server with the imported PoultryFlow realm | `8081` application, `9001` management by default |

Image versions are pinned so that every developer runs the same service versions. Update them deliberately and verify persistence and health checks before merging an upgrade.

## Persistence design

One PostgreSQL container keeps local operations simple while maintaining logical separation:

- `POSTGRES_DB`, `POSTGRES_USER`, and `POSTGRES_PASSWORD` configure the PoultryFlow database.
- `KEYCLOAK_DB_NAME`, `KEYCLOAK_DB_USER`, and `KEYCLOAK_DB_PASSWORD` configure a separate database and login owned by Keycloak.
- `infrastructure/postgres/init/01-create-keycloak-database.sh` creates the Keycloak role and database only when the PostgreSQL volume is initialized for the first time.
- The Compose-managed `postgres_data` volume stores both databases. Keycloak does not use its embedded development database.

The Spring Boot application remains independent from PostgreSQL but validates Keycloak access tokens when protected API routes are called. Application datasource configuration and migrations remain deferred.

## PoultryFlow realm import

`infrastructure/keycloak/poultryflow-realm.json` is mounted read-only into `/opt/keycloak/data/import/`. Keycloak starts with `--import-realm` and creates the enabled `poultryflow` development realm when it is absent.

The imported realm defines:

- `poultryflow-web`, a public browser client using Standard Authorization Code Flow with PKCE S256;
- exact localhost redirect URIs and the `http://localhost:5173` web origin;
- `poultryflow-api`, a bearer-only logical API audience;
- an audience mapper that adds `poultryflow-api` to frontend access tokens;
- `OWNER`, `MANAGER`, `STAFF`, `ACCOUNTANT`, and `VIEWER` as non-composite `poultryflow-api` client roles emitted through Keycloak's standard client-role claim when assigned.

It does not contain users, passwords, role assignments, client secrets, or production configuration.

Keycloak startup import skips a realm that already exists. This preserves local state during ordinary restarts, but it also means changes to the committed JSON are not applied automatically to an existing local `poultryflow` realm.

## Environment configuration

Copy the committed development template before starting the stack:

```bash
cp .env.example .env
```

`.env.example` contains development-only sample values for every required Compose variable. `.env` is ignored by Git and is the appropriate place for workstation overrides.

| Variable | Purpose |
| --- | --- |
| `POSTGRES_DB` | PoultryFlow database name |
| `POSTGRES_USER` | PoultryFlow database owner |
| `POSTGRES_PASSWORD` | PoultryFlow database password |
| `POSTGRES_PORT` | PostgreSQL host port |
| `KEYCLOAK_DB_NAME` | Keycloak database name |
| `KEYCLOAK_DB_USER` | Keycloak database owner |
| `KEYCLOAK_DB_PASSWORD` | Keycloak database password |
| `KEYCLOAK_BOOTSTRAP_ADMIN_USERNAME` | Initial local Keycloak administrator |
| `KEYCLOAK_BOOTSTRAP_ADMIN_PASSWORD` | Initial local Keycloak administrator password |
| `KEYCLOAK_PORT` | Keycloak HTTP host port |
| `KEYCLOAK_MANAGEMENT_PORT` | Keycloak health and management host port |

These settings are not a production profile. Production must use managed secrets, hardened Keycloak startup, TLS, restricted network exposure, backups, and deployment-specific database configuration.

## Start and inspect

From the repository root:

```bash
docker compose config
docker compose up -d --wait
docker compose ps
```

Useful endpoints and checks:

```bash
curl --fail http://localhost:9001/health/ready
curl --head http://localhost:8081
docker compose logs postgres
docker compose logs keycloak
```

The PostgreSQL health check uses `pg_isready`. The Keycloak health check enables its built-in readiness endpoint and uses Bash TCP support already present in the hardened Keycloak image; it does not assume `curl` or `wget` is installed in the container.

## Stop and restart

Normal shutdown removes containers and the Compose network but retains database data:

```bash
docker compose down
```

Restarting recreates the containers against the existing volume:

```bash
docker compose up -d
```

## Destructive reset

The following command permanently deletes both local databases, including all PoultryFlow and Keycloak data:

```bash
docker compose down --volumes
```

Use it only when a clean local database is required or the development realm must be recreated from the committed import. Database names, owners, passwords, and realm configuration are applied during first initialization; changing them for an existing volume does not recreate users, databases, or the realm automatically.

## Troubleshooting

- Run `docker compose ps` first and wait for both services to report `healthy`.
- Run `docker compose logs <service>` to inspect startup or health failures.
- If ports `5432`, `8081`, or `9001` are already in use, change the corresponding host-port variable in `.env` and recreate the containers.
- Run `docker compose config` to find missing variables or Compose syntax errors.
- Use the destructive reset only when the persisted local data can be discarded.

S3-compatible storage and production deployment infrastructure remain deferred until later stories require them. IoT devices, brokers, microservices, and Kubernetes are outside this stack.
