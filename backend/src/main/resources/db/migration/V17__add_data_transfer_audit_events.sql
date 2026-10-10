ALTER TABLE crm.audit_event DROP CONSTRAINT audit_event_event_type_check;
ALTER TABLE crm.audit_event ADD CONSTRAINT audit_event_event_type_check CHECK (event_type IN (
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
    'LOGIN_SUCCEEDED', 'LOGIN_FAILED', 'LOGOUT_SUCCEEDED', 'ACCESS_DENIED',
    'DATA_IMPORT_PREVIEWED', 'DATA_IMPORT_SUCCEEDED', 'DATA_IMPORT_FAILED',
    'DATA_EXPORT_SUCCEEDED', 'DATA_EXPORT_FAILED'
));

ALTER TABLE crm.audit_event DROP CONSTRAINT audit_event_target_type_check;
ALTER TABLE crm.audit_event ADD CONSTRAINT audit_event_target_type_check CHECK (target_type IN (
    'COMPANY', 'CONTACT', 'LEAD', 'OPPORTUNITY', 'ACTIVITY', 'TASK', 'NOTE', 'SAVED_VIEW',
    'ORGANIZATION', 'ORGANIZATION_MEMBERSHIP', 'USER', 'API_REQUEST', 'DATA_TRANSFER'
));

ALTER TABLE crm.audit_event ADD CONSTRAINT ck_audit_event_safe_metadata CHECK (
    jsonb_typeof(metadata) = 'object'
    AND (metadata - ARRAY['method', 'resourceType', 'recordCount', 'failureCode']) = '{}'::jsonb
    AND (NOT (metadata ? 'method') OR metadata->>'method' IN ('GET', 'POST', 'PUT', 'PATCH', 'DELETE'))
    AND (NOT (metadata ? 'resourceType') OR metadata->>'resourceType' IN ('companies', 'contacts', 'leads', 'opportunities'))
    AND (NOT (metadata ? 'recordCount') OR metadata->>'recordCount' ~ '^(0|[1-9][0-9]{0,5})$')
    AND (NOT (metadata ? 'failureCode') OR metadata->>'failureCode' ~ '^[A-Z_]{1,40}$')
);

COMMENT ON CONSTRAINT ck_audit_event_safe_metadata ON crm.audit_event IS
    'Restricts audit metadata to safe request and data-transfer diagnostic fields; never stores uploaded file contents.';
