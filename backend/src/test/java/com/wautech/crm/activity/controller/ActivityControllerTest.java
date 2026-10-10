package com.wautech.crm.activity.controller;

import com.wautech.crm.platform.tenant.AuthenticatedOrganizationContext;
import com.wautech.crm.platform.security.SecurityMvcTestSupport;
import com.wautech.crm.platform.security.TenantMvcTestConfiguration;
import com.wautech.crm.TestOrganization;
import com.wautech.crm.activity.dto.ActivityResponse;
import com.wautech.crm.activity.entity.ActivityType;
import com.wautech.crm.activity.service.ActivityService;
import com.wautech.crm.activity.service.ActivityNotFoundException;
import com.wautech.crm.company.service.CompanyNotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(ActivityController.class)
@Import(TenantMvcTestConfiguration.class)
class ActivityControllerTest extends SecurityMvcTestSupport {
    @MockitoBean private AuthenticatedOrganizationContext organizationContext;
    @BeforeEach
    void organizationContextUsesTestOrganization() {
        org.mockito.Mockito.when(organizationContext.requireOrganizationId()).thenReturn(com.wautech.crm.TestOrganization.ID);
    }

    @Autowired private MockMvc mockMvc;
    @MockitoBean private ActivityService activityService;

    @ParameterizedTest
    @EnumSource(ActivityType.class)
    void createsEachSupportedActivityType(ActivityType type) throws Exception {
        UUID id = UUID.randomUUID();
        UUID companyId = UUID.randomUUID();
        when(activityService.create(eq(TestOrganization.ID), any())).thenReturn(response(id, companyId, null, null, null, type));

        mockMvc.perform(post("/api/activities").contentType(MediaType.APPLICATION_JSON)
                        .content(requestJson(companyId, null, null, null, type.name(), "Customer discussion", "2026-01-01T10:00:00Z")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(id.toString()))
                .andExpect(jsonPath("$.type").value(type.name()))
                .andExpect(jsonPath("$.companyId").value(companyId.toString()));
    }

    @Test
    void rejectsMissingTypeSubjectOccurredAtAndAllParents() throws Exception {
        UUID companyId = UUID.randomUUID();
        mockMvc.perform(post("/api/activities").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"companyId\":\"" + companyId + "\",\"subject\":\"Call\",\"occurredAt\":\"2026-01-01T10:00:00Z\"}"))
                .andExpect(status().isBadRequest());
        mockMvc.perform(post("/api/activities").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"companyId\":\"" + companyId + "\",\"type\":\"CALL\",\"subject\":\" \",\"occurredAt\":\"2026-01-01T10:00:00Z\"}"))
                .andExpect(status().isBadRequest());
        mockMvc.perform(post("/api/activities").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"companyId\":\"" + companyId + "\",\"type\":\"CALL\",\"subject\":\"Call\"}"))
                .andExpect(status().isBadRequest());
        mockMvc.perform(post("/api/activities").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"type\":\"CALL\",\"subject\":\"Call\",\"occurredAt\":\"2026-01-01T10:00:00Z\"}"))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(activityService);
    }

    @Test
    void rejectsUnsupportedActivityType() throws Exception {
        mockMvc.perform(post("/api/activities").contentType(MediaType.APPLICATION_JSON)
                        .content(requestJson(UUID.randomUUID(), null, null, null, "TASK", "Follow up", "2026-01-01T10:00:00Z")))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(activityService);
    }

    @Test
    void mapsMissingParentAndMissingOrArchivedActivityToNotFound() throws Exception {
        UUID companyId = UUID.randomUUID();
        when(activityService.create(eq(TestOrganization.ID), any())).thenThrow(new CompanyNotFoundException(companyId));
        mockMvc.perform(post("/api/activities").contentType(MediaType.APPLICATION_JSON)
                        .content(requestJson(companyId, null, null, null, "CALL", "Call", "2026-01-01T10:00:00Z")))
                .andExpect(status().isNotFound());

        UUID id = UUID.randomUUID();
        when(activityService.getById(TestOrganization.ID, id)).thenThrow(new ActivityNotFoundException(id));
        mockMvc.perform(get("/api/activities/{id}", id)).andExpect(status().isNotFound());
    }

    @Test
    void getsAndUpdatesActivity() throws Exception {
        UUID id = UUID.randomUUID();
        UUID companyId = UUID.randomUUID();
        when(activityService.getById(TestOrganization.ID, id)).thenReturn(response(id, companyId, null, null, null, ActivityType.CALL));
        when(activityService.update(eq(TestOrganization.ID), eq(id), any()))
                .thenReturn(response(id, companyId, null, null, null, ActivityType.EMAIL));

        mockMvc.perform(get("/api/activities/{id}", id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.type").value("CALL"));
        mockMvc.perform(put("/api/activities/{id}", id).contentType(MediaType.APPLICATION_JSON)
                        .content(requestJson(companyId, null, null, null, "EMAIL", "Updated", "2026-01-02T10:00:00Z")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.type").value("EMAIL"));
    }

    @Test
    void listForwardsAllFiltersTogetherAndIndividually() throws Exception {
        UUID companyId = UUID.randomUUID();
        UUID contactId = UUID.randomUUID();
        UUID leadId = UUID.randomUUID();
        UUID opportunityId = UUID.randomUUID();
        when(activityService.listActive(TestOrganization.ID, companyId, contactId, leadId, opportunityId, ActivityType.CALL))
                .thenReturn(List.of());
        when(activityService.listActive(TestOrganization.ID, companyId, null, null, null, null)).thenReturn(List.of());
        when(activityService.listActive(TestOrganization.ID, null, contactId, null, null, null)).thenReturn(List.of());
        when(activityService.listActive(TestOrganization.ID, null, null, leadId, null, null)).thenReturn(List.of());
        when(activityService.listActive(TestOrganization.ID, null, null, null, opportunityId, null)).thenReturn(List.of());
        when(activityService.listActive(TestOrganization.ID, null, null, null, null, ActivityType.NOTE)).thenReturn(List.of());

        mockMvc.perform(get("/api/activities").param("companyId", companyId.toString())
                        .param("contactId", contactId.toString()).param("leadId", leadId.toString())
                        .param("opportunityId", opportunityId.toString()).param("type", "CALL"))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/activities").param("companyId", companyId.toString())).andExpect(status().isOk());
        mockMvc.perform(get("/api/activities").param("contactId", contactId.toString())).andExpect(status().isOk());
        mockMvc.perform(get("/api/activities").param("leadId", leadId.toString())).andExpect(status().isOk());
        mockMvc.perform(get("/api/activities").param("opportunityId", opportunityId.toString())).andExpect(status().isOk());
        mockMvc.perform(get("/api/activities").param("type", "NOTE")).andExpect(status().isOk());

        verify(activityService).listActive(TestOrganization.ID, companyId, contactId, leadId, opportunityId, ActivityType.CALL);
        verify(activityService).listActive(TestOrganization.ID, companyId, null, null, null, null);
        verify(activityService).listActive(TestOrganization.ID, null, contactId, null, null, null);
        verify(activityService).listActive(TestOrganization.ID, null, null, leadId, null, null);
        verify(activityService).listActive(TestOrganization.ID, null, null, null, opportunityId, null);
        verify(activityService).listActive(TestOrganization.ID, null, null, null, null, ActivityType.NOTE);
    }

    @Test
    void listForwardsSearchAndSortAlongsideActivityType() throws Exception {
        when(activityService.listActive(TestOrganization.ID, null, null, null, null, ActivityType.CALL,
                "intro", "subject", "asc")).thenReturn(List.of());
        mockMvc.perform(get("/api/activities").param("type", "CALL").param("search", "intro")
                        .param("sortBy", "subject").param("sortDirection", "asc"))
                .andExpect(status().isOk());
        verify(activityService).listActive(TestOrganization.ID, null, null, null, null, ActivityType.CALL,
                "intro", "subject", "asc");
    }

    @Test
    void rejectsInvalidFilterValues() throws Exception {
        mockMvc.perform(get("/api/activities").param("companyId", "not-a-uuid")).andExpect(status().isBadRequest());
        mockMvc.perform(get("/api/activities").param("type", "TASK")).andExpect(status().isBadRequest());
    }

    @Test
    void deletePerformsSoftArchiveEndpoint() throws Exception {
        mockMvc.perform(delete("/api/activities/{id}", UUID.randomUUID()))
                .andExpect(status().isNoContent());
    }

    private String requestJson(UUID companyId, UUID contactId, UUID leadId, UUID opportunityId,
                               String type, String subject, String occurredAt) {
        StringBuilder json = new StringBuilder("{");
        appendUuid(json, "companyId", companyId);
        appendUuid(json, "contactId", contactId);
        appendUuid(json, "leadId", leadId);
        appendUuid(json, "opportunityId", opportunityId);
        if (type != null) json.append("\"type\":\"").append(type).append("\",");
        if (subject != null) json.append("\"subject\":\"").append(subject).append("\",");
        if (occurredAt != null) json.append("\"occurredAt\":\"").append(occurredAt).append("\",");
        if (json.charAt(json.length() - 1) == ',') json.setLength(json.length() - 1);
        return json.append('}').toString();
    }

    private void appendUuid(StringBuilder json, String field, UUID value) {
        if (value != null) json.append('"').append(field).append("\":\"").append(value).append("\",");
    }

    private ActivityResponse response(UUID id, UUID companyId, UUID contactId, UUID leadId,
                                      UUID opportunityId, ActivityType type) {
        Instant now = Instant.parse("2026-01-01T10:00:00Z");
        return new ActivityResponse(id, companyId, contactId, leadId, opportunityId, type,
                "Customer discussion", "Discussed next steps", now, now, now, false);
    }
}
