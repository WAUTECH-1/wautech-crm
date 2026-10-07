CREATE TABLE crm.saved_view (
    id UUID PRIMARY KEY,
    name VARCHAR(200) NOT NULL CHECK (name ~ '[^[:space:]]'),
    resource VARCHAR(20) NOT NULL
        CHECK (resource IN ('COMPANY', 'CONTACT', 'LEAD', 'OPPORTUNITY', 'ACTIVITY', 'TASK', 'NOTE')),
    configuration_version INTEGER NOT NULL CHECK (configuration_version > 0),
    configuration JSONB NOT NULL CHECK (jsonb_typeof(configuration) = 'object'),
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    archived BOOLEAN NOT NULL DEFAULT FALSE
);

CREATE INDEX idx_saved_view_active_name ON crm.saved_view (name ASC, id ASC) WHERE archived = FALSE;
