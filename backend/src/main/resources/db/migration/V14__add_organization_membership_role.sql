ALTER TABLE crm.organization_membership
    ADD COLUMN role VARCHAR(16);

-- Legacy memberships retain read access without receiving write or administrator authority.
UPDATE crm.organization_membership
SET role = 'VIEWER';

ALTER TABLE crm.organization_membership
    ALTER COLUMN role SET NOT NULL,
    ALTER COLUMN role SET DEFAULT 'VIEWER',
    ADD CONSTRAINT ck_membership_role CHECK (role IN ('OWNER', 'ADMIN', 'SALES_USER', 'VIEWER'));

COMMENT ON COLUMN crm.organization_membership.role IS
    'Organization-scoped role. Legacy memberships migrate to VIEWER pending controlled owner bootstrap and review.';
