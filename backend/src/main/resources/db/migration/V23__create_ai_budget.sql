CREATE TABLE crm.ai_budget (
 budget_key VARCHAR(200) PRIMARY KEY,
 used BIGINT NOT NULL CHECK (used >= 0)
);
COMMENT ON TABLE crm.ai_budget IS 'Durable AI token reservations and rate windows; no prompts, model responses, credentials or CRM text.';
