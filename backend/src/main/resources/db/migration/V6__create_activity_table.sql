CREATE TABLE crm.activity (
    id UUID PRIMARY KEY,
    company_id UUID REFERENCES crm.company (id),
    contact_id UUID REFERENCES crm.contact (id),
    lead_id UUID REFERENCES crm.lead (id),
    opportunity_id UUID REFERENCES crm.opportunity (id),
    type VARCHAR(20) NOT NULL
        CHECK (type IN ('CALL', 'EMAIL', 'MEETING', 'NOTE')),
    subject VARCHAR(200) NOT NULL,
    description TEXT,
    occurred_at TIMESTAMPTZ NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    archived BOOLEAN NOT NULL DEFAULT FALSE,
    CONSTRAINT chk_activity_has_parent CHECK (
        company_id IS NOT NULL OR contact_id IS NOT NULL OR lead_id IS NOT NULL OR opportunity_id IS NOT NULL
    )
);

CREATE INDEX idx_activity_active_occurred_at ON crm.activity (occurred_at DESC) WHERE archived = FALSE;
CREATE INDEX idx_activity_active_company_occurred_at ON crm.activity (company_id, occurred_at DESC) WHERE archived = FALSE;
CREATE INDEX idx_activity_active_contact_occurred_at ON crm.activity (contact_id, occurred_at DESC) WHERE archived = FALSE;
CREATE INDEX idx_activity_active_lead_occurred_at ON crm.activity (lead_id, occurred_at DESC) WHERE archived = FALSE;
CREATE INDEX idx_activity_active_opportunity_occurred_at ON crm.activity (opportunity_id, occurred_at DESC) WHERE archived = FALSE;
CREATE INDEX idx_activity_active_type_occurred_at ON crm.activity (type, occurred_at DESC) WHERE archived = FALSE;
