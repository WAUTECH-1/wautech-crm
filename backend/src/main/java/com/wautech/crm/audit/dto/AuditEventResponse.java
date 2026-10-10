package com.wautech.crm.audit.dto;

import com.wautech.crm.audit.entity.AuditEvent;
import com.wautech.crm.audit.entity.AuditOutcome;

import java.time.Instant;
import java.util.UUID;

/** Public audit view deliberately omits internal metadata. */
public record AuditEventResponse(UUID id, UUID actorUserId, String eventType, String targetType,
        UUID targetId, AuditOutcome outcome, Instant occurredAt) {
    public static AuditEventResponse from(AuditEvent event) {
        return new AuditEventResponse(event.getId(), event.getActorUserId(), event.getEventType(),
                event.getTargetType(), event.getTargetId(), event.getOutcome(), event.getOccurredAt());
    }
}
