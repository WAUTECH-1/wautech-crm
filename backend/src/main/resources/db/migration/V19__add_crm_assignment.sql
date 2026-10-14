ALTER TABLE crm.task ADD COLUMN assignee_user_id UUID;
ALTER TABLE crm.opportunity ADD COLUMN owner_user_id UUID;
ALTER TABLE crm.task ADD CONSTRAINT fk_task_assignee_membership FOREIGN KEY (organization_id,assignee_user_id) REFERENCES crm.organization_membership(organization_id,user_id);
ALTER TABLE crm.opportunity ADD CONSTRAINT fk_opportunity_owner_membership FOREIGN KEY (organization_id,owner_user_id) REFERENCES crm.organization_membership(organization_id,user_id);
CREATE INDEX idx_task_assignee ON crm.task(organization_id,assignee_user_id) WHERE archived=false;
CREATE INDEX idx_opportunity_owner ON crm.opportunity(organization_id,owner_user_id) WHERE archived=false;
