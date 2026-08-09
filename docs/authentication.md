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
  client roles: poultryflow-api
              |
              | access token
              | aud: poultryflow-api
              | resource_access.poultryflow-api.roles
              v
Spring Boot Resource Server
  public: documented infrastructure endpoints only
  protected: all other routes
```

The browser client and API audience are separate Keycloak clients:

- `poultryflow-web` is a public client. It has no client secret because browser applications cannot protect one.
- `poultryflow-api` is a bearer-only logical resource representation. It has no interactive login flow or reusable backend client secret.
- The `poultryflow-api-audience` protocol mapper adds `poultryflow-api` only to access tokens issued through `poultryflow-web`.
- `OWNER`, `MANAGER`, `STAFF`, `ACCOUNTANT`, and `VIEWER` are client roles on `poultryflow-api`, not realm roles.

These roles are coarse application authorization roles. They do not replace the future `FarmMembership` model: when memberships exist, access to a target farm must satisfy both the application policy and that user's membership for the farm.

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

## Credential recovery

Credential recovery remains entirely within Keycloak. While unauthenticated, PoultryFlow displays **Forgot password?** next to **Sign in** and delegates the action to Keycloak's Reset credentials flow. PoultryFlow does not collect usernames, email addresses, or passwords for recovery and has no password-reset API.

The frontend asks `keycloak-js` to generate a normal login URL with the existing application redirect. It parses that URL, verifies that its path ends in `/auth`, and replaces only that final segment with `/forgot-credentials`. The state, nonce, client ID, redirect URI, and PKCE parameters generated and retained by the adapter remain unchanged, so a completed reset and login returns through the normal OIDC callback.

PoultryFlow never indicates whether an account exists and never renders raw Keycloak errors. URL-generation or redirect failures produce the same generic authentication error used by sign-in. Keycloak owns all reset-screen and account-enumeration-resistant messaging.

## Token policy

Access, refresh, and ID tokens remain inside the Keycloak adapter in browser memory. PoultryFlow does not persist them in localStorage, sessionStorage, IndexedDB, cookies, or application state, and does not log or render them. The frontend exposes only the allowlisted `poultryflow-api` roles as typed in-memory authentication state.

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

## Role mapping and authorization

Keycloak places assigned API client roles in the standard access-token claim:

```json
{
  "resource_access": {
    "poultryflow-api": {
      "roles": ["STAFF"]
    }
  }
}
```

The backend maps only known values at `resource_access.poultryflow-api.roles` to `ROLE_OWNER`, `ROLE_MANAGER`, `ROLE_STAFF`, `ROLE_ACCOUNTANT`, and `ROLE_VIEWER`. Realm roles, roles for other clients, malformed claims, and unknown names are ignored. Role checks use method security at controller or use-case boundaries; the central HTTP configuration remains a fail-closed authentication boundary.

The frontend provides `hasRole`, `hasAnyRole`, and `RequireRole` for role-aware navigation and rendering. These checks improve the user experience only. Backend authorization is authoritative.

## URL boundaries

These routes are public:

- `/actuator/health`
- `/v3/api-docs` and `/v3/api-docs/**`
- `/swagger-ui.html` and `/swagger-ui/**`

All other routes, including `/api/v1/**`, require an access token whose audience contains `poultryflow-api`. A missing or rejected token produces HTTP 401 with `application/problem+json`, `code: AUTHENTICATION_REQUIRED`, and `WWW-Authenticate: Bearer`.

An authenticated request that fails a role policy produces HTTP 403 with `application/problem+json` and `code: AUTHORIZATION_DENIED`. The response does not expose token data or Spring Security exceptions. The backend logs a warning with only `event=authorization_denied`, the authenticated principal, HTTP method, and request path for future audit integration; persistent audit storage is not implemented yet.

## Local realm import

The development realm is stored in `infrastructure/keycloak/poultryflow-realm.json` and mounted read-only into Keycloak's `/opt/keycloak/data/import/` directory. `docker compose up -d` starts Keycloak with `--import-realm`. The import defines the five `poultryflow-api` client roles but contains no users, passwords, role assignments, or client secrets.

The realm enables Keycloak's Reset credentials flow but intentionally contains no SMTP server or credentials. Keycloak requires a separately configured SMTP provider to deliver time-limited reset links. Local Compose does not include a permanent email catcher; production SMTP settings and secrets belong to deployment configuration. PoultryFlow never emails passwords, and SMS recovery is not configured for the MVP.

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
6. Open that user's **Role mapping** tab, select **Assign role**, filter by clients, and assign one or more roles belonging to `poultryflow-api`.
7. Start the backend from `backend/` with `mvn spring-boot:run`.
8. Start the frontend from `frontend/` with `npm run dev`.
9. Open `http://localhost:5173` and select **Sign in**. Credentials are entered only on the Keycloak-hosted page.
10. Select **Sign out** to clear PoultryFlow's in-memory state, initiate Keycloak logout, and return to the frontend.
11. While signed out, select **Forgot password?** to open Keycloak's Reset credentials screen. Email delivery works only after an SMTP provider is configured directly in Keycloak for the current environment.

If a persisted local realm predates US-006, startup import will not add the roles to it. Add the exact five client roles under **Clients > poultryflow-api > Roles**, or use the documented destructive reset only when all local data is disposable.

Keycloak's discovery document is available at `http://localhost:8081/realms/poultryflow/.well-known/openid-configuration`.

## Offline impact

Authentication and credential recovery require network access to Keycloak. Recovery also requires access to the configured email provider and has no offline mode. PoultryFlow does not cache credentials or tokens to provide offline login. Future offline workflows may define access to previously authorized local business data, but they do not include offline authentication, recovery, or synchronization in US-007.

## Deferred security work

- A future farm-management story must constrain farm-scoped access with `FarmMembership`; coarse roles alone are insufficient.
- The audit epic owns durable storage and querying of authorization-denied events.
- US-008 owns advanced secure session lifecycle and timeout policies.
