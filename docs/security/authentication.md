# Authentication and local security setup

## API contract

1. `GET /api/auth/csrf` creates/returns the session CSRF token. Send the returned token in the `X-CSRF-TOKEN` header for state-changing requests.
2. `POST /api/auth/login` accepts `{"email":"...","password":"..."}` and establishes a server-side session. Invalid email/password and disabled or unprovisioned users return the same `401` problem detail.
3. `GET /api/auth/me` returns the signed-in user's profile from Spring Security's authenticated principal. It does not accept a user ID.
4. `POST /api/auth/logout` invalidates the current session and returns `204`; a valid CSRF token is required.

All tenant-sensitive CRM API requests also require `X-Organization-ID`. The server checks that the signed-in user is enabled, that the organization exists and is not archived, and that the user has an `ACTIVE` membership before setting the trusted organization request attribute. The ID header selects a tenant context but is not authorization by itself. Suspended, revoked, missing, and cross-organization memberships are rejected.

## Credential storage and provisioning

Migration V13 adds nullable `password_hash` to the existing global user table. `NULL` represents a Feature #12 user who has not completed a trusted credential setup. The login provider uses BCrypt; password hashes are never mapped into API DTOs. The internal `CredentialProvisioningService` accepts an initial password once, applies a 12-byte minimum and BCrypt's 72-byte maximum, and uses an atomic update so concurrent provisioning cannot replace a credential.

No registration, invitation acceptance, or password recovery endpoint returns or emails a token. Those workflows need a verified out-of-band delivery mechanism, which is not configured. Existing users therefore need a separately approved, trusted onboarding path before they can sign in; do not set a common default password or update hashes directly in production SQL.

## Session, CSRF, and deployment

Sessions are stored by the local servlet container and expire after 30 minutes of inactivity. The session cookie is HTTP-only and SameSite=Lax. Set `SESSION_COOKIE_SECURE=true` when serving over HTTPS. For multiple application instances, choose and configure a shared Spring Session store and test invalidation across instances before deployment. No Redis or other session infrastructure is added here.

CSRF protection is enabled for all unsafe methods, including login and logout. The API does not enable cross-origin credentialed CORS. A separate frontend origin requires a reviewed allowlist and coordinated CSRF-cookie/session configuration.

## Authorization boundary

Authentication answers who is signed in. Active membership and active organization checks enforce the tenant boundary for each CRM API request. This feature adds no roles or permissions; Feature #14 must implement RBAC and protect administrative operations. The health endpoint and CSRF bootstrap endpoint are the only GET routes made public; login is the only public state-changing route and remains CSRF-protected.

## Local verification

Run `cmd /c mvnw.cmd test` and `cmd /c mvnw.cmd verify` from `backend`. PostgreSQL must be available to execute Flyway V13 and validate the JPA schema. The application expects local HTTP by default (`SESSION_COOKIE_SECURE=false`); use HTTPS and set the secure-cookie environment variable for deployed environments.
