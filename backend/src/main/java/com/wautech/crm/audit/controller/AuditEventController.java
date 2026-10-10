package com.wautech.crm.audit.controller;

import com.wautech.crm.audit.dto.AuditEventPageResponse;
import com.wautech.crm.audit.entity.AuditOutcome;
import com.wautech.crm.audit.service.AuditQueryService;
import com.wautech.crm.platform.tenant.AuthenticatedOrganizationContext;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.UUID;

@RestController
@RequestMapping("/api/audit-events")
public class AuditEventController {
    private final AuditQueryService service;
    private final AuthenticatedOrganizationContext organizationContext;

    public AuditEventController(AuditQueryService service, AuthenticatedOrganizationContext organizationContext) {
        this.service = service;
        this.organizationContext = organizationContext;
    }

    @GetMapping
    public AuditEventPageResponse list(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to,
            @RequestParam(required = false) String eventType,
            @RequestParam(required = false) UUID actorId,
            @RequestParam(required = false) String targetType,
            @RequestParam(required = false) UUID targetId,
            @RequestParam(required = false) AuditOutcome outcome,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size) {
        return AuditEventPageResponse.from(service.list(organizationContext.requireOrganizationId(), from, to,
                eventType, actorId, targetType, targetId, outcome, page, size));
    }
}
