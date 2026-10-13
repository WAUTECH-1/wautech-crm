# Current-user organization discovery

This branch adds two read-only bootstrap endpoints under the existing authenticated `/api/auth/**` security policy:

- GET `/api/auth/organizations`: caller's ACTIVE memberships in non-archived organizations, ordered by organization name.
- GET `/api/auth/organizations/{organizationId}/membership`: revalidates that caller's membership; generic 403 for unknown, foreign, revoked, suspended or archived targets.

Both return no-store responses containing organizationId, organizationName, membershipId, role and status ACTIVE. No user ID is accepted as authority, no entity or credential is serialized, and no organization selection is persisted in the HTTP session. An enabled persisted principal is required. The query fetches organization associations within the read-only transaction.

Existing CRM requests still require X-Organization-ID, persisted active membership and existing method-level role checks on every request. Existing admin membership management remains unchanged. No schema migration, account change, CORS relaxation or security-filter exemption is added.

The frontend PR depends on these endpoints. Deploy this backend change before enabling the integrated frontend.

## Verification

Run `backend/mvnw.cmd -f backend/pom.xml verify` with Java 25. Controller tests cover anonymous denial, authenticated discovery without tenant headers, ignored impersonation parameters, generic inaccessible-target denial and invalid UUIDs. Service tests cover enabled principal lookup, scoped ACTIVE/non-archived queries, viewer role mapping and denial.

Run `./scripts/Test-UnifiedIntegration.ps1` with Docker running and port 15432 available. It starts a new disposable PostgreSQL 17 container using tmpfs, generates a private fixture password, migrates only the isolated database, and runs real HTTP tests using real persistence/security filters. It stops only its own fixture container afterward. It never reads or modifies existing admin credentials or the normal PostgreSQL volume.

The opt-in test is gated to the exact fixture URL `jdbc:postgresql://127.0.0.1:15432/wautech_integration_fixture`. Standard tests skip it unless explicitly enabled. Real HTTP coverage includes login/CSRF/me/logout/disabled-session rejection, organization discovery/revocation/viewer access, tenant/object isolation, all four CRM CRUD workflows, lead/status and opportunity/stage transitions, pipeline/tasks/activities and notification contracts.

Live testing also found an existing PostgreSQL null-parameter inference failure in task dueBefore/dueAfter predicates. Explicit timestamp casts fix omitted date filters while retaining the same API and tenant predicates. The real task-list request is covered by the opt-in test.

Membership discovery currently returns an unpaged list. Assess pagination/rate controls if users acquire very large membership sets. Production HTTPS cookies and proxy configuration still require deployment verification.
