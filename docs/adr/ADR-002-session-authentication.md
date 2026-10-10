# ADR-002 — Browser Session Authentication

## Status

Accepted for Feature #13 implementation.

## Context

WAU TECH CRM is a browser-oriented CRM with global users, organization memberships, and tenant-owned APIs. Authentication provider, session model, and credential provisioning were previously undecided.

## Decision

- Use Spring Security with server-managed HTTP sessions and the existing global `crm.user_account` identity.
- Store only BCrypt password hashes. Use an environment-configurable BCrypt cost (default 12, allowed range 10–16).
- Keep existing Feature #12 users unprovisioned (`password_hash IS NULL`) until a trusted onboarding flow sets a credential. Do not provide a shared password or public registration/credential endpoint.
- Use a session cookie named `WAUTECH_SESSION`, HTTP-only, SameSite=Lax, with `SESSION_COOKIE_SECURE` set to `true` behind HTTPS in deployed environments.
- Use session-backed CSRF tokens for all state-changing requests, including login and logout. `GET /api/auth/csrf` returns a token for same-origin browser clients.
- Require authentication for all API routes except `GET /api/health`, `GET /api/auth/csrf`, and `POST /api/auth/login`.
- Require `X-Organization-ID` on tenant-sensitive API requests. Validate active membership and active organization on every request before populating the server-trusted organization request attribute.
- Re-read account enabled state on authenticated requests; invalidate the session when the account is missing or disabled.
- Keep CORS same-origin by default. Any cross-origin browser client requires a separate explicit trusted-origin decision.

## Consequences

- Spring Security's local servlet session store is suitable only for a single running instance. A multi-instance deployment must add a deliberately configured shared session store and review session revocation behavior before scaling out.
- Password provisioning, password recovery, and invitation acceptance remain unavailable over HTTP until a verified identity/onboarding or delivery channel is designed. The internal one-time credential provisioning service is a building block, not an administrative API.
- Feature #14 must add server-side roles/permissions. Membership validates tenant access but does not grant administrative or CRM role permissions.
- CRM APIs fail closed unless the authenticated user supplies an organization to which they currently have an active membership; the supplied ID alone never grants access.
