CREATE TABLE crm.company (
    id UUID PRIMARY KEY,
    name VARCHAR(200) NOT NULL,
    website VARCHAR(2048),
    industry VARCHAR(120),
    phone VARCHAR(40),
    email VARCHAR(254),
    status VARCHAR(40) NOT NULL DEFAULT 'ACTIVE',
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    archived BOOLEAN NOT NULL DEFAULT FALSE
);

CREATE INDEX idx_company_active_created_at ON crm.company (created_at DESC) WHERE archived = FALSE;
