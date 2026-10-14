package com.wautech.crm.notification.entity;

/** Notification events with a recipient relationship the current domain can identify reliably. */
public enum NotificationType {
  ORGANIZATION_MEMBERSHIP_ACTIVATED,
  TASK_ASSIGNED,
  TASK_DUE,
  OPPORTUNITY_ASSIGNED,
  ORGANIZATION_MEMBERSHIP_ROLE_CHANGED
}
