# PoultryFlow authentication

## Architecture

PoultryFlow delegates authentication to Keycloak. The React application is an OpenID Connect public client, and the Spring Boot API is an OAuth2 Resource Server.

```text
React browser application
  client: poultryflow-web
  Authorization Code + PKCE S256
              |
              v
Keycloak realm: poultryflow
              |
              | access token
              | aud: poultryflow-api
              v
Spring Boot Resource Server
  protected: /api/v1/**
```

The browser client and API audience are separate Keycloak clients:

- `poultryflow-web` is a public client. It has no client secret because browser applications cannot protect one.
- `poultryflow-api` is a bearer-only logical resource representation. It has no interactive login flow or reusable backend client secret.
- The `poultryflow-api-audience` protocol mapper adds `poultryflow-api` only to access tokens issued through `poultryflow-web`.

US-005 distinguishes authenticated from unauthenticated requests only. PoultryFlow business roles and claim-to-authority mapping are deferred to US-006.

## Browser flow

The frontend uses `keycloak-js` with:

- Authorization Code Flow (`flow: standard`)
- PKCE S256
- the adapter's default nonce validation
- `check-sso` during startup
- silent SSO through `/silent-check-sso.html`, with the adapter's safe regular `check-sso` fallback for browsers that restrict third-party cookies

The Keycloak client accepts only these development redirects:

- `http://localhost:5173/`
- `http://localhost:5173/silent-check-sso.html`

Its only web origin is `http://localhost:5173`. Wildcard redirects and origins are not configured.

The adapter is initialized once through a cached promise before authenticated application state is used. This also makes initialization safe when React StrictMode invokes development effects more than once.

## Token policy

Access, refresh, and ID tokens remain inside the Keycloak adapter in browser memory. PoultryFlow does not persist them in localStorage, sessionStorage, IndexedDB, cookies, or application state, and does not log or render them.

The adapter refreshes an expiring access token when needed. A failed refresh clears the in-memory authentication state and requires the user to sign in again without exposing the raw failure.

A full page reload restores authentication, when a Keycloak SSO session is available, through `check-sso`; it does not restore tokens from PoultryFlow storage.

## Backend validation

Spring Security validates bearer access tokens using these public OIDC settings:

| Environment variable | Development default |
| --- | --- |
| `POULTRYFLOW_OIDC_ISSUER_URI` | `http://localhost:8081/realms/poultryflow` |
| `POULTRYFLOW_OIDC_JWK_SET_URI` | `http://localhost:8081/realms/poultryflow/protocol/openid-connect/certs` |
| `POULTRYFLOW_OIDC_AUDIENCE` | `poultryflow-api` |

Configuring both issuer and JWK Set URI lets the backend start without a running Keycloak server while still validating the token signature, temporal claims, issuer, and intended audience when a bearer token is received.

The API is stateless and does not create an HTTP session for bearer authentication. CSRF is ignored only for the `/api/v1/**` bearer-token boundary because it does not authenticate with browser cookies.

## URL boundaries

These routes are public:

- `/actuator/health`
- `/v3/api-docs` and `/v3/api-docs/**`
- `/swagger-ui.html` and `/swagger-ui/**`

All `/api/v1/**` routes require an access token whose audience contains `poultryflow-api`. A missing or rejected token produces HTTP 401 with `application/problem+json`, `code: AUTHENTICATION_REQUIRED`, and `WWW-Authenticate: Bearer`.

## Local realm import

The development realm is stored in `infrastructure/keycloak/poultryflow-realm.json` and mounted read-only into Keycloak's `/opt/keycloak/data/import/` directory. `docker compose up -d` starts Keycloak with `--import-realm`.

Startup import creates the `poultryflow` realm only when it does not already exist. Keycloak skips an existing realm so that normal restarts preserve local users, sessions, and configuration. Editing the committed JSON does not overwrite an existing local realm.

To recreate the realm from the committed import, a developer may run:

```bash
docker compose down --volumes
docker compose up -d --wait
```

The first command is destructive: it permanently deletes all local PoultryFlow and Keycloak PostgreSQL data. It is not part of normal startup or shutdown.

## Local sign-in workflow

1. Copy `.env.example` to `.env` in the repository root.
2. Copy `frontend/.env.example` to `frontend/.env`.
3. Run `docker compose up -d --wait` from the repository root.
4. Open `http://localhost:8081/admin/`, sign in with the local bootstrap administrator from `.env`, and select the `poultryflow` realm.
5. Create a local development user and set a local password. Do not add that user or password to the realm JSON or any repository file.
6. Start the backend from `backend/` with `mvn spring-boot:run`.
7. Start the frontend from `frontend/` with `npm run dev`.
8. Open `http://localhost:5173` and select **Sign in**. Credentials are entered only on the Keycloak-hosted page.
9. Select **Sign out** to clear PoultryFlow's in-memory state, initiate Keycloak logout, and return to the frontend.

Keycloak's discovery document is available at `http://localhost:8081/realms/poultryflow/.well-known/openid-configuration`.

## Offline impact

Authentication requires network access to Keycloak. PoultryFlow does not cache credentials or tokens to provide offline login. Future offline workflows may define access to previously authorized local business data, but US-005 does not implement offline authentication or synchronization.

## Deferred security work

- US-006 owns PoultryFlow role mapping and role-based authorization.
- US-007 owns credential recovery behavior.
- US-008 owns advanced secure session lifecycle and timeout policies.
