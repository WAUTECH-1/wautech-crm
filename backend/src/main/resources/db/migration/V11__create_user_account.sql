CREATE TABLE crm.user_account (
    id UUID PRIMARY KEY,
    email VARCHAR(254) NOT NULL,
    first_name VARCHAR(100) NOT NULL CHECK (length(btrim(first_name, E' \t\r\n')) > 0),
    last_name VARCHAR(100) NOT NULL CHECK (length(btrim(last_name, E' \t\r\n')) > 0),
    enabled BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT ck_user_email_normalized CHECK (email = lower(btrim(email)) AND length(email) > 0),
    CONSTRAINT uq_user_account_email UNIQUE (email)
);
