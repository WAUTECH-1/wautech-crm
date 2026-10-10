package com.wautech.crm.lead.controller;

import com.wautech.crm.platform.tenant.AuthenticatedOrganizationContext;
import com.wautech.crm.TestOrganization;
import com.wautech.crm.company.service.CompanyNotFoundException;
import com.wautech.crm.lead.dto.LeadResponse;
import com.wautech.crm.lead.entity.IllegalLeadStatusTransitionException;
import com.wautech.crm.lead.entity.LeadStatus;
import com.wautech.crm.lead.service.LeadNotFoundException;
import com.wautech.crm.lead.service.LeadService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(LeadController.class)
class LeadControllerTest {
    @MockitoBean private AuthenticatedOrganizationContext organizationContext;
    @BeforeEach
    void organizationContextUsesTestOrganization() {
        org.mockito.Mockito.when(organizationContext.requireOrganizationId()).thenReturn(com.wautech.crm.TestOrganization.ID);
    }

    @Autowired private MockMvc mockMvc;
    @MockitoBean private LeadService leadService;

    @Test
    void createAcceptsOptionalCompanyAndReturnsNewLead() throws Exception {
        UUID id = UUID.randomUUID();
        when(leadService.create(eq(TestOrganization.ID), any())).thenReturn(response(id, null, LeadStatus.NEW));

        mockMvc.perform(post("/api/leads").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"firstName\":\"Ada\",\"lastName\":\"Lovelace\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(id.toString()))
                .andExpect(jsonPath("$.companyId").doesNotExist())
                .andExpect(jsonPath("$.status").value("NEW"));
    }

    @Test
    void createValidatesNamesEmailAndLengths() throws Exception {
        mockMvc.perform(post("/api/leads").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"firstName\":\" \",\"lastName\":\"Lovelace\"}"))
                .andExpect(status().isBadRequest());
        mockMvc.perform(post("/api/leads").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"firstName\":\"Ada\",\"lastName\":\"Lovelace\",\"email\":\"invalid\"}"))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(leadService);
    }

    @Test
    void createReturnsNotFoundForInvalidOrArchivedCompany() throws Exception {
        UUID companyId = UUID.randomUUID();
        when(leadService.create(eq(TestOrganization.ID), any())).thenThrow(new CompanyNotFoundException(companyId));

        mockMvc.perform(post("/api/leads").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"companyId\":\"" + companyId + "\",\"firstName\":\"Ada\",\"lastName\":\"Lovelace\"}"))
                .andExpect(status().isNotFound());
    }

    @Test
    void listSupportsStatusCompanyAndCombinedFilters() throws Exception {
        UUID companyId = UUID.randomUUID();
        when(leadService.listActive(TestOrganization.ID, LeadStatus.NEW, companyId)).thenReturn(List.of(response(UUID.randomUUID(), companyId, LeadStatus.NEW)));

        mockMvc.perform(get("/api/leads").param("status", "NEW").param("companyId", companyId.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].status").value("NEW"));
        verify(leadService).listActive(TestOrganization.ID, LeadStatus.NEW, companyId);
    }

    @Test
    void listForwardsSearchAndSortAlongsideExistingFilters() throws Exception {
        UUID companyId = UUID.randomUUID();
        when(leadService.listActive(TestOrganization.ID, LeadStatus.NEW, companyId, "ada", "firstName", "asc")).thenReturn(List.of());
        mockMvc.perform(get("/api/leads").param("status", "NEW").param("companyId", companyId.toString())
                        .param("search", "ada").param("sortBy", "firstName").param("sortDirection", "asc"))
                .andExpect(status().isOk());
        verify(leadService).listActive(TestOrganization.ID, LeadStatus.NEW, companyId, "ada", "firstName", "asc");
    }

    @Test
    void listRejectsInvalidFilters() throws Exception {
        mockMvc.perform(get("/api/leads").param("status", "BOGUS")).andExpect(status().isBadRequest());
        mockMvc.perform(get("/api/leads").param("companyId", "not-a-uuid")).andExpect(status().isBadRequest());
        verifyNoInteractions(leadService);
    }

    @Test
    void missingLeadReturnsNotFound() throws Exception {
        UUID id = UUID.randomUUID();
        when(leadService.getById(TestOrganization.ID, id)).thenThrow(new LeadNotFoundException(id));

        mockMvc.perform(get("/api/leads/{id}", id))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.detail").value("Lead with ID '" + id + "' was not found"));
    }

    @Test
    void putUpdatesDetailsWithoutStatusField() throws Exception {
        UUID id = UUID.randomUUID();
        when(leadService.update(eq(TestOrganization.ID), eq(id), any())).thenReturn(response(id, null, LeadStatus.NEW));

        mockMvc.perform(put("/api/leads/{id}", id).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"firstName\":\"Ada\",\"lastName\":\"Lovelace\",\"status\":\"QUALIFIED\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("NEW"));
    }

    @Test
    void patchChangesStatusAndRejectsIllegalTransition() throws Exception {
        UUID id = UUID.randomUUID();
        when(leadService.changeStatus(TestOrganization.ID, id, LeadStatus.CONTACTED)).thenReturn(response(id, null, LeadStatus.CONTACTED));
        when(leadService.changeStatus(TestOrganization.ID, id, LeadStatus.NEW))
                .thenThrow(new IllegalLeadStatusTransitionException(LeadStatus.NEW, LeadStatus.NEW));

        mockMvc.perform(patch("/api/leads/{id}/status", id).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"CONTACTED\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("CONTACTED"));
        mockMvc.perform(patch("/api/leads/{id}/status", id).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"NEW\"}"))
                .andExpect(status().isBadRequest());
        mockMvc.perform(patch("/api/leads/{id}/status", id).contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void archiveReturnsNoContent() throws Exception {
        mockMvc.perform(delete("/api/leads/{id}", UUID.randomUUID())).andExpect(status().isNoContent());
    }

    private LeadResponse response(UUID id, UUID companyId, LeadStatus status) {
        Instant now = Instant.parse("2026-01-01T00:00:00Z");
        return new LeadResponse(id, companyId, "Ada", "Lovelace", null, null, null, status, now, now, false);
    }
}
