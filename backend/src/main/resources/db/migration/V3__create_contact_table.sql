CREATE TABLE crm.contact (
    id UUID PRIMARY KEY,
    company_id UUID NOT NULL REFERENCES crm.company (id),
    first_name VARCHAR(100) NOT NULL,
    last_name VARCHAR(100) NOT NULL,
    email VARCHAR(254),
    phone VARCHAR(40),
    job_title VARCHAR(120),
    status VARCHAR(40) NOT NULL DEFAULT 'ACTIVE',
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    archived BOOLEAN NOT NULL DEFAULT FALSE
);

CREATE INDEX idx_contact_active_created_at ON crm.contact (created_at DESC) WHERE archived = FALSE;
CREATE INDEX idx_contact_active_company_created_at ON crm.contact (company_id, created_at DESC) WHERE archived = FALSE;
