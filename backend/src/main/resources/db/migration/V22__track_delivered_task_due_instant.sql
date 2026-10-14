ALTER TABLE crm.task ADD COLUMN reminder_due_at TIMESTAMPTZ;
ALTER TABLE crm.task ADD COLUMN reminder_user_id UUID;
