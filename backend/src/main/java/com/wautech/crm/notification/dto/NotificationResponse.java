package com.wautech.crm.notification.dto;

import com.wautech.crm.notification.entity.Notification;
import com.wautech.crm.notification.entity.NotificationTargetType;
import com.wautech.crm.notification.entity.NotificationType;

import java.time.Instant;
import java.util.UUID;

public record NotificationResponse(UUID id, NotificationType type, String title, String message,
                                  NotificationTargetType targetType, UUID targetId,
                                  Instant createdAt, Instant readAt) {
    public static NotificationResponse from(Notification notification) {
        return new NotificationResponse(notification.getId(), notification.getType(), notification.getTitle(),
                notification.getMessage(), notification.getTargetType(), notification.getTargetId(),
                notification.getCreatedAt(), notification.getReadAt());
    }
}
