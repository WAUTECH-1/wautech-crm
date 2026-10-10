package com.wautech.crm.audit.service;

import com.wautech.crm.audit.entity.AuditEvent;
import com.wautech.crm.audit.entity.AuditOutcome;
import com.wautech.crm.audit.repository.AuditEventRepository;
import com.wautech.crm.organization.service.OrganizationService;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class AuditQueryServiceTest {
    private final AuditEventRepository repository = mock(AuditEventRepository.class);
    private final OrganizationService organizationService = mock(OrganizationService.class);
    private final AuditQueryService service = new AuditQueryService(repository, organizationService);
    private final UUID organizationId = UUID.randomUUID();

    @Test
    void scopesResultsAndReturnsOnlyPublicFieldsInNewestFirstPage() {
        UUID actorId = UUID.randomUUID();
        UUID targetId = UUID.randomUUID();
        Instant time = Instant.parse("2026-10-10T12:00:00Z");
        var event = new AuditEvent(organizationId, actorId, "COMPANY_CREATED", "COMPANY", targetId,
                AuditOutcome.SUCCESS, time, Map.of("method", "GET"));
        when(repository.findOrganizationEvents(eq(organizationId), any(), any(), eq("COMPANY_CREATED"),
                eq(actorId), eq("COMPANY"), eq(targetId), eq(AuditOutcome.SUCCESS), any()))
                .thenReturn(new PageImpl<>(List.of(event), PageRequest.of(0, 20,
                        Sort.by(Sort.Order.desc("occurredAt"), Sort.Order.desc("id"))), 1));

        var page = service.list(organizationId, null, null, " company_created ", actorId, "company", targetId,
                AuditOutcome.SUCCESS, 0, 20);

        assertEquals(1, page.getTotalElements());
        assertEquals("COMPANY_CREATED", page.getContent().getFirst().eventType());
        assertFalse(java.util.Arrays.stream(page.getContent().getFirst().getClass().getRecordComponents())
                .anyMatch(component -> component.getName().equalsIgnoreCase("metadata")));
        verify(repository).findOrganizationEvents(eq(organizationId), any(), any(), eq("COMPANY_CREATED"),
                eq(actorId), eq("COMPANY"), eq(targetId), eq(AuditOutcome.SUCCESS), any());
    }

    @Test
    void rejectsInvalidDateRangeAndUnboundedPageSize() {
        assertThrows(InvalidAuditQueryException.class, () -> service.list(organizationId,
                Instant.parse("2026-10-11T00:00:00Z"), Instant.parse("2026-10-10T00:00:00Z"),
                null, null, null, null, null, 0, 50));
        assertThrows(InvalidAuditQueryException.class, () -> service.list(organizationId,
                null, null, null, null, null, null, null, 0, 101));
        verifyNoInteractions(repository);
    }

    @Test
    void rejectsMalformedFilterValues() {
        assertThrows(InvalidAuditQueryException.class, () -> service.list(organizationId,
                null, null, "COMPANY_CREATED OR 1=1", null, null, null, null, 0, 50));
        verifyNoInteractions(repository);
    }
}
