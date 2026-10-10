-- One-time, operator-run bootstrap for an organization with no OWNER.
-- Replace both UUID placeholders after the organization and intended user are reviewed.
-- Run this as a restricted database operator in a session where the target schema is crm.
BEGIN;

DO $$
DECLARE
    target_organization_id UUID := '00000000-0000-0000-0000-000000000000'; -- replace
    target_membership_id UUID := '00000000-0000-0000-0000-000000000000'; -- replace
    target_membership UUID;
BEGIN
    PERFORM 1
    FROM crm.organization o
    WHERE o.id = target_organization_id AND o.archived = FALSE
    FOR UPDATE;
    IF NOT FOUND THEN
        RAISE EXCEPTION 'Target organization is missing or archived';
    END IF;

    SELECT m.id INTO target_membership
    FROM crm.organization_membership m
    JOIN crm.user_account u ON u.id = m.user_id
    WHERE m.id = target_membership_id
      AND m.organization_id = target_organization_id
      AND m.status = 'ACTIVE'
      AND u.enabled = TRUE
    FOR UPDATE OF m;
    IF NOT FOUND THEN
        RAISE EXCEPTION 'Target membership must belong to this organization and be active for an enabled user';
    END IF;

    IF EXISTS (
        SELECT 1 FROM crm.organization_membership m
        WHERE m.organization_id = target_organization_id
          AND m.status = 'ACTIVE'
          AND m.role = 'OWNER'
    ) THEN
        RAISE EXCEPTION 'Organization already has an active OWNER';
    END IF;

    UPDATE crm.organization_membership
    SET role = 'OWNER', updated_at = CURRENT_TIMESTAMP
    WHERE id = target_membership;
END $$;

COMMIT;
