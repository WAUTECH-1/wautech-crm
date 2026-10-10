package com.wautech.crm.notification.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "notification", schema = "crm")
public class Notification {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false, updatable = false)
    private UUID organizationId;

    @Column(nullable = false, updatable = false)
    private UUID recipientUserId;

    @Enumerated(EnumType.STRING)
    @Column(name = "notification_type", nullable = false, length = 60, updatable = false)
    private NotificationType type;

    @Column(nullable = false, length = 200, updatable = false)
    private String title;

    @Column(nullable = false, length = 1000, updatable = false)
    private String message;

    @Enumerated(EnumType.STRING)
    @Column(length = 50, updatable = false)
    private NotificationTargetType targetType;

    @Column(updatable = false)
    private UUID targetId;

    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    private Instant readAt;

    @Column(length = 200, updatable = false)
    private String deduplicationKey;

    protected Notification() { }

    public Notification(UUID organizationId, UUID recipientUserId, NotificationType type, String title,
                        String message, NotificationTargetType targetType, UUID targetId,
                        String deduplicationKey) {
        this.organizationId = organizationId;
        this.recipientUserId = recipientUserId;
        this.type = type;
        this.title = title;
        this.message = message;
        this.targetType = targetType;
        this.targetId = targetId;
        this.deduplicationKey = deduplicationKey;
    }

    public void markRead(Instant readTime) {
        if (readAt == null) readAt = readTime;
    }

    @PrePersist
    void setCreatedAt() {
        if (createdAt == null) createdAt = Instant.now();
    }

    public UUID getId() { return id; }
    public UUID getOrganizationId() { return organizationId; }
    public UUID getRecipientUserId() { return recipientUserId; }
    public NotificationType getType() { return type; }
    public String getTitle() { return title; }
    public String getMessage() { return message; }
    public NotificationTargetType getTargetType() { return targetType; }
    public UUID getTargetId() { return targetId; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getReadAt() { return readAt; }
    public String getDeduplicationKey() { return deduplicationKey; }
}
