# ADR-003 — Organization-scoped authorization

## Status

Accepted for Feature #14.

## Decision

- Store one `OrganizationRole` on each `OrganizationMembership`; roles are not global user attributes.
- Use `OWNER`, `ADMIN`, `SALES_USER`, and `VIEWER` as the initial fixed role set. A separate role-permission schema is unnecessary for the current four-role policy.
- Enforce permissions with Spring method security on domain service entry points. The shared policy rechecks the authenticated principal, enabled account, active organization, trusted organization request attribute, active membership, and persisted role.
- Keep repository queries organization-scoped as a second tenant boundary. Do not add a platform-administrator bypass.
- Migrate all existing memberships, including pending ones, to `VIEWER`. This preserves read access and grants no write or organization-administration authority implicitly.
- Provision the first OWNER only through a reviewed, operator-run database transaction that names one active organization membership and verifies the associated account is enabled. There is no public bootstrap endpoint. The product owner must identify the initial membership before executing it.

## Permission policy

| Permission | OWNER | ADMIN | SALES_USER | VIEWER |
|---|---:|---:|---:|---:|
| View CRM records and pipeline | Yes | Yes | Yes | Yes |
| Create, update, archive CRM records and manage saved views | Yes | Yes | Yes | No |
| View organization membership and settings | Yes | Yes | No | No |
| Invite/manage membership status | Yes | Yes | No | No |
| Assign SALES_USER or VIEWER | Yes | Yes | No | No |
| Assign ADMIN | Yes | No | No | No |
| Assign or transfer OWNER | Controlled owner transfer only | No | No | No |

An ADMIN cannot assign ADMIN or OWNER; this is the restricted ADMIN assignment policy. A caller cannot change their own membership role. Ownership transfer requires a separate operation and atomically promotes the active target to OWNER while demoting the current OWNER to ADMIN. An active OWNER cannot be suspended, revoked, or demoted if that would leave no active OWNER.

## Authorization flow

Spring Security authenticates the session. The existing tenant filter validates the requested `X-Organization-ID` and puts it in a server-trusted request attribute. Method security then loads the authenticated user and active membership from persistence, checks that the account and organization remain active, confirms the trusted organization matches the requested service organization, and applies the role policy. Business repositories continue to constrain records by organization ID. Missing authentication returns 401; insufficient role returns a generic 403; organization-scoped resource lookups preserve their existing 404 behavior.

## Administration and limitations

Feature #12 exposes membership operations only as internal service methods; Feature #14 does not add HTTP administration routes because the repository has no established membership controller and secure invitation acceptance is still unavailable. Internal role/status service operations are protected with the same method-security policy. A user can belong to different organizations with different roles.

The platform-administrator role in the SRS is deferred until privileged-access, tenant-boundary, and audit controls are approved. The first OWNER bootstrap requires an operator-selected active membership; no user or organization is privileged automatically. Role changes and owner transfer are not yet written to an audit trail because the project has no audit-event module.
