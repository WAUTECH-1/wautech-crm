CREATE TABLE crm.task (
    id UUID PRIMARY KEY,
    company_id UUID REFERENCES crm.company (id),
    contact_id UUID REFERENCES crm.contact (id),
    lead_id UUID REFERENCES crm.lead (id),
    opportunity_id UUID REFERENCES crm.opportunity (id),
    title VARCHAR(200) NOT NULL,
    description TEXT,
    status VARCHAR(20) NOT NULL DEFAULT 'OPEN'
        CHECK (status IN ('OPEN', 'IN_PROGRESS', 'COMPLETED', 'CANCELLED')),
    priority VARCHAR(20) NOT NULL DEFAULT 'NORMAL'
        CHECK (priority IN ('LOW', 'NORMAL', 'HIGH', 'URGENT')),
    due_at TIMESTAMPTZ,
    completed_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    archived BOOLEAN NOT NULL DEFAULT FALSE,
    CONSTRAINT chk_task_has_parent CHECK (
        company_id IS NOT NULL OR contact_id IS NOT NULL OR lead_id IS NOT NULL OR opportunity_id IS NOT NULL
    ),
    CONSTRAINT chk_task_completion_timestamp CHECK (
        (status = 'COMPLETED' AND completed_at IS NOT NULL)
        OR (status <> 'COMPLETED' AND completed_at IS NULL)
    )
);

CREATE INDEX idx_task_active_due_created ON crm.task (due_at ASC, created_at DESC) WHERE archived = FALSE;
CREATE INDEX idx_task_active_status_due ON crm.task (status, due_at ASC) WHERE archived = FALSE;
CREATE INDEX idx_task_active_priority_due ON crm.task (priority, due_at ASC) WHERE archived = FALSE;
CREATE INDEX idx_task_active_company_due ON crm.task (company_id, due_at ASC, created_at DESC) WHERE archived = FALSE;
CREATE INDEX idx_task_active_contact_due ON crm.task (contact_id, due_at ASC, created_at DESC) WHERE archived = FALSE;
CREATE INDEX idx_task_active_lead_due ON crm.task (lead_id, due_at ASC, created_at DESC) WHERE archived = FALSE;
CREATE INDEX idx_task_active_opportunity_due ON crm.task (opportunity_id, due_at ASC, created_at DESC) WHERE archived = FALSE;
