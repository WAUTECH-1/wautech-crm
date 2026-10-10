CREATE TABLE crm.notification (
    id UUID PRIMARY KEY,
    organization_id UUID NOT NULL,
    recipient_user_id UUID NOT NULL,
    notification_type VARCHAR(60) NOT NULL CHECK (notification_type IN (
        'ORGANIZATION_MEMBERSHIP_ACTIVATED', 'ORGANIZATION_MEMBERSHIP_ROLE_CHANGED'
    )),
    title VARCHAR(200) NOT NULL CHECK (length(btrim(title)) > 0),
    message VARCHAR(1000) NOT NULL CHECK (length(btrim(message)) > 0),
    target_type VARCHAR(50) CHECK (target_type IN ('ORGANIZATION_MEMBERSHIP')),
    target_id UUID,
    created_at TIMESTAMPTZ NOT NULL,
    read_at TIMESTAMPTZ,
    deduplication_key VARCHAR(200),
    CONSTRAINT fk_notification_recipient_membership
        FOREIGN KEY (organization_id, recipient_user_id)
        REFERENCES crm.organization_membership (organization_id, user_id) ON DELETE RESTRICT,
    CONSTRAINT ck_notification_target_pair CHECK ((target_type IS NULL) = (target_id IS NULL)),
    CONSTRAINT uq_notification_recipient_dedupe
        UNIQUE (organization_id, recipient_user_id, deduplication_key)
);

CREATE INDEX ix_notification_recipient_org_created
    ON crm.notification (recipient_user_id, organization_id, created_at DESC, id DESC);
CREATE INDEX ix_notification_recipient_org_unread
    ON crm.notification (recipient_user_id, organization_id)
    WHERE read_at IS NULL;

COMMENT ON TABLE crm.notification IS
    'Recipient-specific in-app notifications; organization and recipient are constrained to an existing membership.';
