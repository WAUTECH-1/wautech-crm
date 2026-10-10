CREATE TABLE crm.audit_event (
    id UUID PRIMARY KEY,
    organization_id UUID NULL REFERENCES crm.organization(id) ON DELETE RESTRICT,
    actor_user_id UUID NULL REFERENCES crm.user_account(id) ON DELETE RESTRICT,
    event_type VARCHAR(80) NOT NULL CHECK (event_type IN (
        'COMPANY_CREATED', 'COMPANY_UPDATED', 'COMPANY_ARCHIVED',
        'CONTACT_CREATED', 'CONTACT_UPDATED', 'CONTACT_ARCHIVED',
        'LEAD_CREATED', 'LEAD_UPDATED', 'LEAD_STATUS_CHANGED', 'LEAD_ARCHIVED',
        'OPPORTUNITY_CREATED', 'OPPORTUNITY_UPDATED', 'OPPORTUNITY_STAGE_CHANGED', 'OPPORTUNITY_ARCHIVED',
        'ACTIVITY_CREATED', 'ACTIVITY_UPDATED', 'ACTIVITY_ARCHIVED',
        'TASK_CREATED', 'TASK_UPDATED', 'TASK_STATUS_CHANGED', 'TASK_ARCHIVED',
        'NOTE_CREATED', 'NOTE_UPDATED', 'NOTE_ARCHIVED',
        'SAVED_VIEW_CREATED', 'SAVED_VIEW_UPDATED', 'SAVED_VIEW_ARCHIVED',
        'ORGANIZATION_CREATED', 'ORGANIZATION_UPDATED', 'ORGANIZATION_ARCHIVED',
        'MEMBERSHIP_CREATED', 'MEMBERSHIP_STATUS_CHANGED', 'MEMBERSHIP_ROLE_CHANGED',
        'ORGANIZATION_OWNERSHIP_TRANSFERRED', 'USER_ACTIVATION_CHANGED',
        'LOGIN_SUCCEEDED', 'LOGIN_FAILED', 'LOGOUT_SUCCEEDED', 'ACCESS_DENIED'
    )),
    target_type VARCHAR(50) NOT NULL CHECK (target_type IN (
        'COMPANY', 'CONTACT', 'LEAD', 'OPPORTUNITY', 'ACTIVITY', 'TASK', 'NOTE', 'SAVED_VIEW',
        'ORGANIZATION', 'ORGANIZATION_MEMBERSHIP', 'USER', 'API_REQUEST'
    )),
    target_id UUID NULL,
    outcome VARCHAR(10) NOT NULL CHECK (outcome IN ('SUCCESS', 'FAILURE')),
    occurred_at TIMESTAMPTZ NOT NULL,
    metadata JSONB NOT NULL DEFAULT '{}'::jsonb
);

CREATE INDEX ix_audit_event_org_occurred_id ON crm.audit_event (organization_id, occurred_at DESC, id DESC);
CREATE INDEX ix_audit_event_org_event_occurred ON crm.audit_event (organization_id, event_type, occurred_at DESC);
CREATE INDEX ix_audit_event_org_actor_occurred ON crm.audit_event (organization_id, actor_user_id, occurred_at DESC);
CREATE INDEX ix_audit_event_org_target ON crm.audit_event (organization_id, target_type, target_id, occurred_at DESC);
CREATE INDEX ix_audit_event_platform_occurred ON crm.audit_event (occurred_at DESC, id DESC) WHERE organization_id IS NULL;

CREATE FUNCTION crm.reject_audit_event_mutation() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
    RAISE EXCEPTION 'Audit events are append-only';
END;
$$;

CREATE TRIGGER trg_audit_event_append_only
    BEFORE UPDATE OR DELETE ON crm.audit_event
    FOR EACH ROW EXECUTE FUNCTION crm.reject_audit_event_mutation();

COMMENT ON TABLE crm.audit_event IS
    'Append-only business and security audit events. Database owners/superusers can bypass trigger protections.';
