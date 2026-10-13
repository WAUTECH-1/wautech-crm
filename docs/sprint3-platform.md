# Sprint 3 administration and self-profile API

Baseline: PR #20 at 39e8421731c1c799c9e0ec5a92a03eed92e26150 remains open. Origin/main was fetched and this branch includes it. Dedicated branch: feature/sprint3-wajahat-platform. Existing branch and all database/migration state are preserved.

New endpoints:
- GET/PATCH /api/organization: current organization profile/name, ADMIN/OWNER.
- GET/POST /api/organization/memberships: current organization members; create INVITED/VIEWER membership for an existing user UUID. No email delivery or account provisioning.
- PATCH /api/organization/memberships/{id}/status: existing lifecycle transitions; last-owner, enabled-user and owner-authority safeguards.
- PATCH /api/organization/memberships/{id}/role: existing assignment/self-change/owner safeguards.
- GET/PATCH /api/auth/profile: enabled authenticated principal only; first/last name edits, immutable email, no-store responses, profile audit event.

All writes require existing CSRF/session policies. All organization IDs come from AuthenticatedOrganizationContext after the existing tenant filter; membership lookup uses ID plus organization ID. OrganizationMembershipService/OrganizationService method security is reused. OWNER/ADMIN can manage; SALES_USER/VIEWER cannot list or mutate organization administration. No speculative MEMBER role is introduced. Self role changes are forbidden; admins cannot modify owners; the last active owner cannot be removed. Request records use Bean Validation. No migrations, security relaxation or real-account updates.

Verification: Maven Wrapper verify on Java 25 passed, including executable packaging. 360 tests discovered: 356 passed and four opt-in live tests skipped. New controller tests combine real tenant/security filters, organization/membership services and method authorization with mocked persistence; they cover tenant isolation, foreign membership lookup, RBAC, CSRF, DTO validation and creation/role/activation/suspension. Self-profile service tests verify authenticated principal binding and email preservation; controller tests verify anonymous/CSRF/validation/no-store behavior. Existing service/owner/security regressions remain green.

No new live HTTP/PostgreSQL administration test was run. Before release, exercise these endpoints against disposable users/database, then verify staging HTTPS/cookies/CSRF and audit persistence. No database records were modified for this delivery.

Review/merge order: backend #20, frontend #8, backend Sprint 3, frontend Sprint 3. The Sprint 3 PR targets main and includes its unmerged predecessor until #20 lands. Never merge automatically. Frontend delivery report contains the feature/permission matrix, registration boundary and exact final three-developer integration steps.
