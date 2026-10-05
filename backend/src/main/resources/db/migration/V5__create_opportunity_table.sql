CREATE TABLE crm.opportunity (
    id UUID PRIMARY KEY,
    name VARCHAR(200) NOT NULL,
    description TEXT,
    amount NUMERIC(19, 4),
    currency VARCHAR(3),
    stage VARCHAR(30) NOT NULL DEFAULT 'QUALIFICATION'
        CHECK (stage IN ('QUALIFICATION', 'NEEDS_ANALYSIS', 'PROPOSAL', 'NEGOTIATION', 'CLOSED_WON', 'CLOSED_LOST')),
    expected_close_date DATE,
    company_id UUID NOT NULL REFERENCES crm.company (id),
    contact_id UUID REFERENCES crm.contact (id),
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    archived BOOLEAN NOT NULL DEFAULT FALSE,
    CONSTRAINT chk_opportunity_amount_currency_pair CHECK ((amount IS NULL) = (currency IS NULL)),
    CONSTRAINT chk_opportunity_amount_nonnegative CHECK (amount IS NULL OR amount >= 0),
    CONSTRAINT chk_opportunity_currency_format CHECK (currency IS NULL OR currency ~ '^[A-Z]{3}$')
);

CREATE INDEX idx_opportunity_active_created_at ON crm.opportunity (created_at DESC) WHERE archived = FALSE;
CREATE INDEX idx_opportunity_active_company_created_at ON crm.opportunity (company_id, created_at DESC) WHERE archived = FALSE;
CREATE INDEX idx_opportunity_active_contact_created_at ON crm.opportunity (contact_id, created_at DESC) WHERE archived = FALSE;
CREATE INDEX idx_opportunity_active_stage_created_at ON crm.opportunity (stage, created_at DESC) WHERE archived = FALSE;
