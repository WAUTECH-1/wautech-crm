package com.wautech.crm.audit.controller;

import com.wautech.crm.audit.dto.AuditEventResponse;
import com.wautech.crm.audit.entity.AuditOutcome;
import com.wautech.crm.audit.service.AuditQueryService;
import com.wautech.crm.platform.security.SecurityMvcTestSupport;
import com.wautech.crm.platform.security.TenantMvcTestConfiguration;
import com.wautech.crm.platform.tenant.AuthenticatedOrganizationContext;
import com.wautech.crm.platform.test.SecurityTestIdentity;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(AuditEventController.class)
@Import(TenantMvcTestConfiguration.class)
class AuditEventControllerTest extends SecurityMvcTestSupport {
    @Autowired private MockMvc mockMvc;
    @MockitoBean private AuditQueryService auditQueryService;
    @MockitoBean private AuthenticatedOrganizationContext organizationContext;

    @BeforeEach
    void setOrganizationContext() {
        when(organizationContext.requireOrganizationId()).thenReturn(SecurityTestIdentity.ORGANIZATION_ID);
    }

    @Test
    void forwardsOnlyTrustedTenantAndSupportedFilters() throws Exception {
        UUID actorId = UUID.randomUUID();
        UUID targetId = UUID.randomUUID();
        var response = new AuditEventResponse(UUID.randomUUID(), actorId, "COMPANY_CREATED", "COMPANY", targetId,
                AuditOutcome.SUCCESS, Instant.parse("2026-10-10T12:00:00Z"));
        when(auditQueryService.list(eq(SecurityTestIdentity.ORGANIZATION_ID), any(), any(), eq("COMPANY_CREATED"),
                eq(actorId), eq("COMPANY"), eq(targetId), eq(AuditOutcome.SUCCESS), eq(0), eq(25)))
                .thenReturn(new PageImpl<>(List.of(response), PageRequest.of(0, 25), 1));

        mockMvc.perform(get("/api/audit-events")
                        .param("from", "2026-10-01T00:00:00Z")
                        .param("eventType", "COMPANY_CREATED")
                        .param("actorId", actorId.toString())
                        .param("targetType", "COMPANY")
                        .param("targetId", targetId.toString())
                        .param("outcome", "SUCCESS")
                        .param("page", "0").param("size", "25"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].eventType").value("COMPANY_CREATED"))
                .andExpect(jsonPath("$.content[0].metadata").doesNotExist());

        verify(auditQueryService).list(eq(SecurityTestIdentity.ORGANIZATION_ID), any(), isNull(),
                eq("COMPANY_CREATED"), eq(actorId), eq("COMPANY"), eq(targetId), eq(AuditOutcome.SUCCESS),
                eq(0), eq(25));
    }
}
