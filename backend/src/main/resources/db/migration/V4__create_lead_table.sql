CREATE TABLE crm.lead (
    id UUID PRIMARY KEY,
    company_id UUID REFERENCES crm.company(id),
    first_name VARCHAR(100) NOT NULL,
    last_name VARCHAR(100) NOT NULL,
    email VARCHAR(254),
    phone VARCHAR(40),
    job_title VARCHAR(120),
    status VARCHAR(20) NOT NULL DEFAULT 'NEW'
        CHECK (status IN ('NEW', 'CONTACTED', 'QUALIFIED', 'DISQUALIFIED')),
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    archived BOOLEAN NOT NULL DEFAULT FALSE
);

CREATE INDEX idx_lead_active_created_at ON crm.lead (created_at DESC) WHERE archived = FALSE;
CREATE INDEX idx_lead_active_status_created_at ON crm.lead (status, created_at DESC) WHERE archived = FALSE;
CREATE INDEX idx_lead_active_company_created_at ON crm.lead (company_id, created_at DESC) WHERE archived = FALSE;
