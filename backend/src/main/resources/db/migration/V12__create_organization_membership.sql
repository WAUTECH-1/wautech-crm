CREATE TABLE crm.organization_membership (
    id UUID PRIMARY KEY,
    organization_id UUID NOT NULL REFERENCES crm.organization (id),
    user_id UUID NOT NULL REFERENCES crm.user_account (id),
    status VARCHAR(16) NOT NULL DEFAULT 'INVITED',
    invited_at TIMESTAMPTZ,
    joined_at TIMESTAMPTZ,
    deactivated_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT uq_membership_organization_user UNIQUE (organization_id, user_id),
    CONSTRAINT ck_membership_status CHECK (status IN ('INVITED', 'ACTIVE', 'SUSPENDED', 'REVOKED'))
);

CREATE INDEX idx_membership_organization_created
    ON crm.organization_membership (organization_id, created_at DESC);
