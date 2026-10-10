ALTER TABLE crm.user_account
    ADD COLUMN password_hash VARCHAR(100);

COMMENT ON COLUMN crm.user_account.password_hash IS
    'Adaptive password hash. NULL means the user has not completed a trusted credential-provisioning workflow.';
