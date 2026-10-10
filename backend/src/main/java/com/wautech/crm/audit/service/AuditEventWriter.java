package com.wautech.crm.audit.service;

import com.wautech.crm.audit.entity.AuditEvent;
import com.wautech.crm.audit.entity.AuditOutcome;
import com.wautech.crm.audit.repository.AuditEventRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

@Service
public class AuditEventWriter {
    private final AuditEventRepository repository;

    public AuditEventWriter(AuditEventRepository repository) { this.repository = repository; }

    /** Joins the business transaction so failure to persist the event rolls the mutation back. */
    @Transactional
    public void recordBusiness(UUID organizationId, UUID actorUserId, String eventType, String targetType,
            UUID targetId) {
        repository.save(new AuditEvent(organizationId, actorUserId, eventType, targetType, targetId,
                AuditOutcome.SUCCESS, Instant.now(), Map.of()));
    }

    /** Persists authentication and filter events independently of a business transaction. */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void recordSecurity(UUID organizationId, UUID actorUserId, String eventType, String targetType,
            UUID targetId, AuditOutcome outcome, Map<String, String> safeMetadata) {
        repository.save(new AuditEvent(organizationId, actorUserId, eventType, targetType, targetId,
                outcome, Instant.now(), safeMetadata));
    }
}
