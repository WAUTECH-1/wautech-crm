CREATE TABLE crm.organization (
    id UUID PRIMARY KEY,
    name VARCHAR(200) NOT NULL CHECK (length(btrim(name, E' \t\r\n')) > 0),
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    archived BOOLEAN NOT NULL DEFAULT FALSE
);

-- A single generated legacy organization owns every row created before tenancy.
CREATE TEMPORARY TABLE migration_legacy_organization ON COMMIT DROP AS
SELECT gen_random_uuid() AS id;

INSERT INTO crm.organization (id, name, created_at, updated_at, archived)
SELECT id, 'Legacy Organization', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, FALSE
FROM migration_legacy_organization;

ALTER TABLE crm.company ADD COLUMN organization_id UUID;
ALTER TABLE crm.contact ADD COLUMN organization_id UUID;
ALTER TABLE crm.lead ADD COLUMN organization_id UUID;
ALTER TABLE crm.opportunity ADD COLUMN organization_id UUID;
ALTER TABLE crm.activity ADD COLUMN organization_id UUID;
ALTER TABLE crm.task ADD COLUMN organization_id UUID;
ALTER TABLE crm.note ADD COLUMN organization_id UUID;
ALTER TABLE crm.saved_view ADD COLUMN organization_id UUID;

UPDATE crm.company SET organization_id = (SELECT id FROM migration_legacy_organization);
UPDATE crm.contact SET organization_id = (SELECT id FROM migration_legacy_organization);
UPDATE crm.lead SET organization_id = (SELECT id FROM migration_legacy_organization);
UPDATE crm.opportunity SET organization_id = (SELECT id FROM migration_legacy_organization);
UPDATE crm.activity SET organization_id = (SELECT id FROM migration_legacy_organization);
UPDATE crm.task SET organization_id = (SELECT id FROM migration_legacy_organization);
UPDATE crm.note SET organization_id = (SELECT id FROM migration_legacy_organization);
UPDATE crm.saved_view SET organization_id = (SELECT id FROM migration_legacy_organization);

ALTER TABLE crm.company ALTER COLUMN organization_id SET NOT NULL;
ALTER TABLE crm.contact ALTER COLUMN organization_id SET NOT NULL;
ALTER TABLE crm.lead ALTER COLUMN organization_id SET NOT NULL;
ALTER TABLE crm.opportunity ALTER COLUMN organization_id SET NOT NULL;
ALTER TABLE crm.activity ALTER COLUMN organization_id SET NOT NULL;
ALTER TABLE crm.task ALTER COLUMN organization_id SET NOT NULL;
ALTER TABLE crm.note ALTER COLUMN organization_id SET NOT NULL;
ALTER TABLE crm.saved_view ALTER COLUMN organization_id SET NOT NULL;

ALTER TABLE crm.company ADD CONSTRAINT fk_company_organization
    FOREIGN KEY (organization_id) REFERENCES crm.organization (id);
ALTER TABLE crm.contact ADD CONSTRAINT fk_contact_organization
    FOREIGN KEY (organization_id) REFERENCES crm.organization (id);
ALTER TABLE crm.lead ADD CONSTRAINT fk_lead_organization
    FOREIGN KEY (organization_id) REFERENCES crm.organization (id);
ALTER TABLE crm.opportunity ADD CONSTRAINT fk_opportunity_organization
    FOREIGN KEY (organization_id) REFERENCES crm.organization (id);
ALTER TABLE crm.activity ADD CONSTRAINT fk_activity_organization
    FOREIGN KEY (organization_id) REFERENCES crm.organization (id);
ALTER TABLE crm.task ADD CONSTRAINT fk_task_organization
    FOREIGN KEY (organization_id) REFERENCES crm.organization (id);
ALTER TABLE crm.note ADD CONSTRAINT fk_note_organization
    FOREIGN KEY (organization_id) REFERENCES crm.organization (id);
ALTER TABLE crm.saved_view ADD CONSTRAINT fk_saved_view_organization
    FOREIGN KEY (organization_id) REFERENCES crm.organization (id);

ALTER TABLE crm.company ADD CONSTRAINT uq_company_organization_id UNIQUE (organization_id, id);
ALTER TABLE crm.contact ADD CONSTRAINT uq_contact_organization_id UNIQUE (organization_id, id);
ALTER TABLE crm.lead ADD CONSTRAINT uq_lead_organization_id UNIQUE (organization_id, id);
ALTER TABLE crm.opportunity ADD CONSTRAINT uq_opportunity_organization_id UNIQUE (organization_id, id);
ALTER TABLE crm.activity ADD CONSTRAINT uq_activity_organization_id UNIQUE (organization_id, id);
ALTER TABLE crm.task ADD CONSTRAINT uq_task_organization_id UNIQUE (organization_id, id);
ALTER TABLE crm.note ADD CONSTRAINT uq_note_organization_id UNIQUE (organization_id, id);
ALTER TABLE crm.saved_view ADD CONSTRAINT uq_saved_view_organization_id UNIQUE (organization_id, id);

-- Composite parent keys make cross-organization links impossible even if application code regresses.
ALTER TABLE crm.contact ADD CONSTRAINT fk_contact_company_organization
    FOREIGN KEY (organization_id, company_id) REFERENCES crm.company (organization_id, id);
ALTER TABLE crm.lead ADD CONSTRAINT fk_lead_company_organization
    FOREIGN KEY (organization_id, company_id) REFERENCES crm.company (organization_id, id);
ALTER TABLE crm.opportunity ADD CONSTRAINT fk_opportunity_company_organization
    FOREIGN KEY (organization_id, company_id) REFERENCES crm.company (organization_id, id);
ALTER TABLE crm.opportunity ADD CONSTRAINT fk_opportunity_contact_organization
    FOREIGN KEY (organization_id, contact_id) REFERENCES crm.contact (organization_id, id);

ALTER TABLE crm.activity ADD CONSTRAINT fk_activity_company_organization
    FOREIGN KEY (organization_id, company_id) REFERENCES crm.company (organization_id, id);
ALTER TABLE crm.activity ADD CONSTRAINT fk_activity_contact_organization
    FOREIGN KEY (organization_id, contact_id) REFERENCES crm.contact (organization_id, id);
ALTER TABLE crm.activity ADD CONSTRAINT fk_activity_lead_organization
    FOREIGN KEY (organization_id, lead_id) REFERENCES crm.lead (organization_id, id);
ALTER TABLE crm.activity ADD CONSTRAINT fk_activity_opportunity_organization
    FOREIGN KEY (organization_id, opportunity_id) REFERENCES crm.opportunity (organization_id, id);

ALTER TABLE crm.task ADD CONSTRAINT fk_task_company_organization
    FOREIGN KEY (organization_id, company_id) REFERENCES crm.company (organization_id, id);
ALTER TABLE crm.task ADD CONSTRAINT fk_task_contact_organization
    FOREIGN KEY (organization_id, contact_id) REFERENCES crm.contact (organization_id, id);
ALTER TABLE crm.task ADD CONSTRAINT fk_task_lead_organization
    FOREIGN KEY (organization_id, lead_id) REFERENCES crm.lead (organization_id, id);
ALTER TABLE crm.task ADD CONSTRAINT fk_task_opportunity_organization
    FOREIGN KEY (organization_id, opportunity_id) REFERENCES crm.opportunity (organization_id, id);

ALTER TABLE crm.note ADD CONSTRAINT fk_note_company_organization
    FOREIGN KEY (organization_id, company_id) REFERENCES crm.company (organization_id, id);
ALTER TABLE crm.note ADD CONSTRAINT fk_note_contact_organization
    FOREIGN KEY (organization_id, contact_id) REFERENCES crm.contact (organization_id, id);
ALTER TABLE crm.note ADD CONSTRAINT fk_note_lead_organization
    FOREIGN KEY (organization_id, lead_id) REFERENCES crm.lead (organization_id, id);
ALTER TABLE crm.note ADD CONSTRAINT fk_note_opportunity_organization
    FOREIGN KEY (organization_id, opportunity_id) REFERENCES crm.opportunity (organization_id, id);

-- Replace pre-tenancy list indexes with their tenant-leading equivalents below.
DROP INDEX crm.idx_company_active_created_at;
DROP INDEX crm.idx_contact_active_created_at;
DROP INDEX crm.idx_contact_active_company_created_at;
DROP INDEX crm.idx_lead_active_created_at;
DROP INDEX crm.idx_lead_active_status_created_at;
DROP INDEX crm.idx_lead_active_company_created_at;
DROP INDEX crm.idx_opportunity_active_created_at;
DROP INDEX crm.idx_opportunity_active_company_created_at;
DROP INDEX crm.idx_opportunity_active_contact_created_at;
DROP INDEX crm.idx_opportunity_active_stage_created_at;
DROP INDEX crm.idx_activity_active_occurred_at;
DROP INDEX crm.idx_activity_active_company_occurred_at;
DROP INDEX crm.idx_activity_active_contact_occurred_at;
DROP INDEX crm.idx_activity_active_lead_occurred_at;
DROP INDEX crm.idx_activity_active_opportunity_occurred_at;
DROP INDEX crm.idx_activity_active_type_occurred_at;
DROP INDEX crm.idx_task_active_due_created;
DROP INDEX crm.idx_task_active_status_due;
DROP INDEX crm.idx_task_active_priority_due;
DROP INDEX crm.idx_task_active_company_due;
DROP INDEX crm.idx_task_active_contact_due;
DROP INDEX crm.idx_task_active_lead_due;
DROP INDEX crm.idx_task_active_opportunity_due;
DROP INDEX crm.idx_note_active_updated_created;
DROP INDEX crm.idx_note_active_company_updated_created;
DROP INDEX crm.idx_note_active_contact_updated_created;
DROP INDEX crm.idx_note_active_lead_updated_created;
DROP INDEX crm.idx_note_active_opportunity_updated_created;
DROP INDEX crm.idx_saved_view_active_name;

-- Tenant-leading indexes support current scoped lists, parent filters and saved-view lookup patterns.
CREATE INDEX idx_company_org_active_created ON crm.company (organization_id, created_at DESC) WHERE archived = FALSE;
CREATE INDEX idx_contact_org_active_created ON crm.contact (organization_id, created_at DESC) WHERE archived = FALSE;
CREATE INDEX idx_contact_org_company_created ON crm.contact (organization_id, company_id, created_at DESC) WHERE archived = FALSE;
CREATE INDEX idx_lead_org_active_created ON crm.lead (organization_id, created_at DESC) WHERE archived = FALSE;
CREATE INDEX idx_lead_org_status_created ON crm.lead (organization_id, status, created_at DESC) WHERE archived = FALSE;
CREATE INDEX idx_lead_org_company_created ON crm.lead (organization_id, company_id, created_at DESC) WHERE archived = FALSE;
CREATE INDEX idx_opportunity_org_active_created ON crm.opportunity (organization_id, created_at DESC) WHERE archived = FALSE;
CREATE INDEX idx_opportunity_org_company_created ON crm.opportunity (organization_id, company_id, created_at DESC) WHERE archived = FALSE;
CREATE INDEX idx_opportunity_org_contact_created ON crm.opportunity (organization_id, contact_id, created_at DESC) WHERE archived = FALSE;
CREATE INDEX idx_opportunity_org_stage_created ON crm.opportunity (organization_id, stage, created_at DESC) WHERE archived = FALSE;
CREATE INDEX idx_activity_org_occurred ON crm.activity (organization_id, occurred_at DESC) WHERE archived = FALSE;
CREATE INDEX idx_activity_org_company_occurred ON crm.activity (organization_id, company_id, occurred_at DESC) WHERE archived = FALSE;
CREATE INDEX idx_activity_org_contact_occurred ON crm.activity (organization_id, contact_id, occurred_at DESC) WHERE archived = FALSE;
CREATE INDEX idx_activity_org_lead_occurred ON crm.activity (organization_id, lead_id, occurred_at DESC) WHERE archived = FALSE;
CREATE INDEX idx_activity_org_opportunity_occurred ON crm.activity (organization_id, opportunity_id, occurred_at DESC) WHERE archived = FALSE;
CREATE INDEX idx_activity_org_type_occurred ON crm.activity (organization_id, type, occurred_at DESC) WHERE archived = FALSE;
CREATE INDEX idx_task_org_due_created ON crm.task (organization_id, due_at ASC, created_at DESC) WHERE archived = FALSE;
CREATE INDEX idx_task_org_status_due ON crm.task (organization_id, status, due_at ASC) WHERE archived = FALSE;
CREATE INDEX idx_task_org_priority_due ON crm.task (organization_id, priority, due_at ASC) WHERE archived = FALSE;
CREATE INDEX idx_task_org_company_due ON crm.task (organization_id, company_id, due_at ASC, created_at DESC) WHERE archived = FALSE;
CREATE INDEX idx_task_org_contact_due ON crm.task (organization_id, contact_id, due_at ASC, created_at DESC) WHERE archived = FALSE;
CREATE INDEX idx_task_org_lead_due ON crm.task (organization_id, lead_id, due_at ASC, created_at DESC) WHERE archived = FALSE;
CREATE INDEX idx_task_org_opportunity_due ON crm.task (organization_id, opportunity_id, due_at ASC, created_at DESC) WHERE archived = FALSE;
CREATE INDEX idx_note_org_updated_created ON crm.note (organization_id, updated_at DESC, created_at DESC) WHERE archived = FALSE;
CREATE INDEX idx_note_org_company_updated ON crm.note (organization_id, company_id, updated_at DESC, created_at DESC) WHERE archived = FALSE;
CREATE INDEX idx_note_org_contact_updated ON crm.note (organization_id, contact_id, updated_at DESC, created_at DESC) WHERE archived = FALSE;
CREATE INDEX idx_note_org_lead_updated ON crm.note (organization_id, lead_id, updated_at DESC, created_at DESC) WHERE archived = FALSE;
CREATE INDEX idx_note_org_opportunity_updated ON crm.note (organization_id, opportunity_id, updated_at DESC, created_at DESC) WHERE archived = FALSE;
CREATE INDEX idx_saved_view_org_active_name ON crm.saved_view (organization_id, name ASC, id ASC) WHERE archived = FALSE;

-- Recovery is forward-only: restore from a verified pre-migration backup if needed, then deploy a corrective
-- additive migration. Do not drop the backfilled organization_id columns or delete legacy rows in place.
