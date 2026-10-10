# Organization ownership migration

Migration `V10__add_organization_tenant_ownership.sql` creates one generated `Legacy Organization` and assigns every pre-existing Company, Contact, Lead, Opportunity, Activity, Task, Note, and Saved View row to it. Existing business rows and relationship columns are retained.

The migration makes `organization_id` mandatory, adds organization foreign keys and `(organization_id, id)` unique keys, and adds composite foreign keys for every existing parent relationship. PostgreSQL checks these keys even if application-level validation is accidentally bypassed.

## Risks

- The migration takes table locks while adding columns, backfilling, and enforcing constraints. It should run in a planned maintenance window if the database contains substantial data.
- Disk usage will increase for the additional ownership indexes and unique constraints.
- All legacy records intentionally share one organization because no historical tenant identity exists in the current schema.

## Recovery

This data migration is forward-recovered. Before applying it to a non-disposable database, take and verify a backup. If a deployment must be reverted, restore that backup or deploy a corrective additive migration; do not delete the organization or clear ownership columns because that would invalidate tenant and relationship constraints.
