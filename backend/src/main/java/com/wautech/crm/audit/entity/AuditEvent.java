package com.wautech.crm.audit.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.Map;
import java.util.LinkedHashMap;
import java.util.UUID;

@Entity
@Table(name = "audit_event", schema = "crm")
public class AuditEvent {
    @Id
    private UUID id;

    @Column(name = "organization_id")
    private UUID organizationId;

    @Column(name = "actor_user_id")
    private UUID actorUserId;

    @Column(name = "event_type", nullable = false, length = 80, updatable = false)
    private String eventType;

    @Column(name = "target_type", nullable = false, length = 50, updatable = false)
    private String targetType;

    @Column(name = "target_id", updatable = false)
    private UUID targetId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10, updatable = false)
    private AuditOutcome outcome;

    @Column(name = "occurred_at", nullable = false, updatable = false)
    private Instant occurredAt;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false, columnDefinition = "jsonb", updatable = false)
    private Map<String, String> metadata;

    protected AuditEvent() { }

    public AuditEvent(UUID organizationId, UUID actorUserId, String eventType, String targetType,
            UUID targetId, AuditOutcome outcome, Instant occurredAt, Map<String, String> metadata) {
        this.id = UUID.randomUUID();
        this.organizationId = organizationId;
        this.actorUserId = actorUserId;
        this.eventType = eventType;
        this.targetType = targetType;
        this.targetId = targetId;
        this.outcome = outcome;
        this.occurredAt = occurredAt;
        this.metadata = sanitizedMetadata(metadata);
    }

    private Map<String, String> sanitizedMetadata(Map<String, String> supplied) {
        if (supplied == null || supplied.size() > 10) throw new IllegalArgumentException("Invalid audit metadata");
        Map<String, String> safe = new LinkedHashMap<>();
        supplied.forEach((key, value) -> {
            boolean allowed = switch (key) {
                case "method" -> value != null && java.util.Set.of("GET", "POST", "PUT", "PATCH", "DELETE").contains(value);
                case "recordCount" -> value != null && value.matches("0|[1-9][0-9]{0,5}");
                case "resourceType" -> value != null && java.util.Set.of("companies", "contacts", "leads", "opportunities").contains(value);
                case "failureCode" -> value != null && value.matches("[A-Z_]{1,40}");
                default -> false;
            };
            if (!allowed) {
                throw new IllegalArgumentException("Invalid audit metadata");
            }
            safe.put(key, value);
        });
        return Map.copyOf(safe);
    }

    public UUID getId() { return id; }
    public UUID getOrganizationId() { return organizationId; }
    public UUID getActorUserId() { return actorUserId; }
    public String getEventType() { return eventType; }
    public String getTargetType() { return targetType; }
    public UUID getTargetId() { return targetId; }
    public AuditOutcome getOutcome() { return outcome; }
    public Instant getOccurredAt() { return occurredAt; }
    public Map<String, String> getMetadata() { return metadata; }
}
