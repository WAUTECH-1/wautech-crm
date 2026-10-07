CREATE TABLE crm.note (
    id UUID PRIMARY KEY,
    company_id UUID REFERENCES crm.company (id),
    contact_id UUID REFERENCES crm.contact (id),
    lead_id UUID REFERENCES crm.lead (id),
    opportunity_id UUID REFERENCES crm.opportunity (id),
    title VARCHAR(200) NOT NULL,
    body TEXT NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    archived BOOLEAN NOT NULL DEFAULT FALSE,
    CONSTRAINT chk_note_exactly_one_parent CHECK (
        (company_id IS NOT NULL AND contact_id IS NULL AND lead_id IS NULL AND opportunity_id IS NULL)
        OR (company_id IS NULL AND contact_id IS NOT NULL AND lead_id IS NULL AND opportunity_id IS NULL)
        OR (company_id IS NULL AND contact_id IS NULL AND lead_id IS NOT NULL AND opportunity_id IS NULL)
        OR (company_id IS NULL AND contact_id IS NULL AND lead_id IS NULL AND opportunity_id IS NOT NULL)
    ),
    CONSTRAINT chk_note_body_not_blank CHECK (length(btrim(body, E' \t\r\n')) > 0)
);

CREATE INDEX idx_note_active_updated_created ON crm.note (updated_at DESC, created_at DESC) WHERE archived = FALSE;
CREATE INDEX idx_note_active_company_updated_created ON crm.note (company_id, updated_at DESC, created_at DESC) WHERE archived = FALSE;
CREATE INDEX idx_note_active_contact_updated_created ON crm.note (contact_id, updated_at DESC, created_at DESC) WHERE archived = FALSE;
CREATE INDEX idx_note_active_lead_updated_created ON crm.note (lead_id, updated_at DESC, created_at DESC) WHERE archived = FALSE;
CREATE INDEX idx_note_active_opportunity_updated_created ON crm.note (opportunity_id, updated_at DESC, created_at DESC) WHERE archived = FALSE;
