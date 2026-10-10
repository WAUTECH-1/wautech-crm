# Authorization and organization roles

## Roles

Roles are stored on `crm.organization_membership`, so one global user may be an `ADMIN` in one organization and a `VIEWER` in another.

| Permission | OWNER | ADMIN | SALES_USER | VIEWER |
|---|---:|---:|---:|---:|
| View companies, contacts, leads, opportunities, activities, tasks, notes, saved views, and pipeline | Yes | Yes | Yes | Yes |
| Create, update, or archive those CRM records; create/update/archive saved views | Yes | Yes | Yes | No |
| Manage organization settings and view/manage membership | Yes | Yes | No | No |
| Assign SALES_USER or VIEWER to another membership | Yes | Yes | No | No |
| Assign ADMIN | Yes | No | No | No |
| Transfer ownership | Yes, controlled operation | No | No | No |

OWNER is the highest organization-level role. ADMIN cannot promote themselves or others to ADMIN or OWNER. A user cannot change their own role. OWNER assignment is available only through the owner-transfer operation. The final active OWNER cannot be suspended, revoked, or demoted.

## Decision flow

1. Spring Security authenticates the session and the enabled-user filter rechecks the account.
2. The active-organization filter validates `X-Organization-ID` against the signed-in user’s current active membership and active organization, then sets the trusted request attribute.
3. Method security on service entry points verifies the trusted organization matches the operation, rechecks the account, active organization, and active membership from persistence, then applies the role permission.
4. Existing repository queries continue to scope business resources by organization ID. A resource in another organization is not returned by the scoped lookup.

Authentication failures return 401. Authenticated requests with insufficient role or tenant membership receive a generic 403. Existing organization-scoped missing-resource lookups return 404.

## Migration and first owner

Migration V14 adds a constrained string role to each membership. All legacy rows, regardless of membership status, receive `VIEWER`. This explicit least-privilege policy avoids granting write, ADMIN, or OWNER authority to historic data. Newly created memberships also default to `VIEWER`.

Before an organization can administer members, an operator must complete the controlled first-owner bootstrap for an explicitly selected active membership whose user account is enabled. Use the guarded, one-time transaction in `docs/security/first-owner-bootstrap.sql`, replace both UUID placeholders after review, and record the change through the operational change process. Do not expose a public bootstrap endpoint or promote every legacy member. This repository does not yet include an audit-event module, so role and ownership changes are not persisted to an application audit trail.

## Deferred security work

The SRS Platform Administrator capability remains unavailable. It needs a separately approved privileged-access, audit, and tenant-support design. Membership management remains service-only: there are no HTTP organization/user/membership administration routes in this feature. Invitation acceptance and credential onboarding remain unavailable until their trusted workflows are designed.
