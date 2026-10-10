package com.wautech.crm.audit.service;

import com.wautech.crm.audit.dto.AuditEventResponse;
import com.wautech.crm.audit.entity.AuditOutcome;
import com.wautech.crm.audit.repository.AuditEventRepository;
import com.wautech.crm.organization.service.OrganizationService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

@Service
public class AuditQueryService {
    private final AuditEventRepository repository;
    private final OrganizationService organizationService;

    public AuditQueryService(AuditEventRepository repository, OrganizationService organizationService) {
        this.repository = repository;
        this.organizationService = organizationService;
    }

    @Transactional(readOnly = true)
    @PreAuthorize("@crmAuthorization.canManageOrganization(#p0)")
    public Page<AuditEventResponse> list(UUID organizationId, Instant fromTime, Instant toTime, String eventType,
            UUID actorId, String targetType, UUID targetId, AuditOutcome outcome, int page, int size) {
        organizationService.requireActiveOrganization(organizationId);
        if (fromTime != null && toTime != null && fromTime.isAfter(toTime)) {
            throw new InvalidAuditQueryException("from must be before or equal to to");
        }
        if (page < 0 || size < 1 || size > 100) throw new InvalidAuditQueryException("Invalid page or size");
        String normalizedType = normalize(eventType, 80);
        String normalizedTarget = normalize(targetType, 50);
        return repository.findOrganizationEvents(organizationId, fromTime, toTime, normalizedType, actorId,
                normalizedTarget, targetId, outcome,
                PageRequest.of(page, size, Sort.by(Sort.Order.desc("occurredAt"), Sort.Order.desc("id"))))
                .map(AuditEventResponse::from);
    }

    private String normalize(String value, int limit) {
        if (value == null || value.isBlank()) return null;
        String normalized = value.trim().toUpperCase(java.util.Locale.ROOT);
        if (normalized.length() > limit || !normalized.matches("[A-Z0-9_]+")) {
            throw new InvalidAuditQueryException("Invalid audit filter");
        }
        return normalized;
    }
}
