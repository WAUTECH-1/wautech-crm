ALTER TABLE crm.notification DROP CONSTRAINT notification_notification_type_check;
ALTER TABLE crm.notification ADD CONSTRAINT notification_notification_type_check CHECK(notification_type IN ('ORGANIZATION_MEMBERSHIP_ACTIVATED','ORGANIZATION_MEMBERSHIP_ROLE_CHANGED','TASK_ASSIGNED','TASK_DUE','OPPORTUNITY_ASSIGNED'));
ALTER TABLE crm.notification DROP CONSTRAINT notification_target_type_check;
ALTER TABLE crm.notification ADD CONSTRAINT notification_target_type_check CHECK(target_type IN ('ORGANIZATION_MEMBERSHIP','TASK','OPPORTUNITY'));
