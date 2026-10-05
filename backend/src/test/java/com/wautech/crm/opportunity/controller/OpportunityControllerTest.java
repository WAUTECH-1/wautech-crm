package com.wautech.crm.opportunity.controller;

import com.wautech.crm.company.service.CompanyNotFoundException;
import com.wautech.crm.contact.service.ContactNotFoundException;
import com.wautech.crm.opportunity.dto.OpportunityResponse;
import com.wautech.crm.opportunity.entity.OpportunityStage;
import com.wautech.crm.opportunity.service.OpportunityContactCompanyMismatchException;
import com.wautech.crm.opportunity.service.OpportunityNotFoundException;
import com.wautech.crm.opportunity.service.OpportunityService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(OpportunityController.class)
class OpportunityControllerTest {
    @Autowired private MockMvc mockMvc;
    @MockitoBean private OpportunityService opportunityService;

    @Test
    void createsOpportunity() throws Exception {
        UUID id = UUID.randomUUID();
        UUID companyId = UUID.randomUUID();
        when(opportunityService.create(any())).thenReturn(response(id, companyId, null));

        mockMvc.perform(post("/api/opportunities").contentType(MediaType.APPLICATION_JSON)
                        .content(requestJson(companyId, null, "Acme renewal", null, null)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(id.toString()))
                .andExpect(jsonPath("$.companyId").value(companyId.toString()))
                .andExpect(jsonPath("$.stage").value("QUALIFICATION"));
    }

    @Test
    void rejectsInvalidRequestFields() throws Exception {
        UUID companyId = UUID.randomUUID();
        mockMvc.perform(post("/api/opportunities").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"companyId\":\"" + companyId + "\",\"name\":\" \"}"))
                .andExpect(status().isBadRequest());
        mockMvc.perform(post("/api/opportunities").contentType(MediaType.APPLICATION_JSON)
                        .content(requestJson(null, null, "Deal", null, null)))
                .andExpect(status().isBadRequest());
        mockMvc.perform(post("/api/opportunities").contentType(MediaType.APPLICATION_JSON)
                        .content(requestJson(companyId, null, "Deal", "100.00", null)))
                .andExpect(status().isBadRequest());
        mockMvc.perform(post("/api/opportunities").contentType(MediaType.APPLICATION_JSON)
                        .content(requestJson(companyId, null, "Deal", null, "USD")))
                .andExpect(status().isBadRequest());
        mockMvc.perform(post("/api/opportunities").contentType(MediaType.APPLICATION_JSON)
                        .content(requestJson(companyId, null, "Deal", "-1", "USD")))
                .andExpect(status().isBadRequest());
        mockMvc.perform(post("/api/opportunities").contentType(MediaType.APPLICATION_JSON)
                        .content(requestJson(companyId, null, "Deal", "10", "usd")))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(opportunityService);
    }

    @Test
    void companyAndContactErrorsUseCentralizedProblemResponses() throws Exception {
        UUID companyId = UUID.randomUUID();
        UUID contactId = UUID.randomUUID();
        when(opportunityService.create(any())).thenThrow(new CompanyNotFoundException(companyId));
        mockMvc.perform(post("/api/opportunities").contentType(MediaType.APPLICATION_JSON)
                        .content(requestJson(companyId, null, "Deal", null, null)))
                .andExpect(status().isNotFound());

        reset(opportunityService);
        when(opportunityService.create(any())).thenThrow(new ContactNotFoundException(contactId));
        mockMvc.perform(post("/api/opportunities").contentType(MediaType.APPLICATION_JSON)
                        .content(requestJson(companyId, contactId, "Deal", null, null)))
                .andExpect(status().isNotFound());

        reset(opportunityService);
        when(opportunityService.create(any())).thenThrow(new OpportunityContactCompanyMismatchException(contactId, companyId));
        mockMvc.perform(post("/api/opportunities").contentType(MediaType.APPLICATION_JSON)
                        .content(requestJson(companyId, contactId, "Deal", null, null)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void listSupportsCompanyContactAndStageFilters() throws Exception {
        UUID companyId = UUID.randomUUID();
        UUID contactId = UUID.randomUUID();
        when(opportunityService.listActive(companyId, contactId, OpportunityStage.PROPOSAL)).thenReturn(List.of());

        mockMvc.perform(get("/api/opportunities").param("companyId", companyId.toString())
                        .param("contactId", contactId.toString()).param("stage", "PROPOSAL"))
                .andExpect(status().isOk());

        verify(opportunityService).listActive(companyId, contactId, OpportunityStage.PROPOSAL);
    }

    @Test
    void invalidFiltersAndStageAreRejected() throws Exception {
        mockMvc.perform(get("/api/opportunities").param("companyId", "bad-uuid"))
                .andExpect(status().isBadRequest());
        mockMvc.perform(get("/api/opportunities").param("stage", "UNKNOWN"))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(opportunityService);
    }

    @Test
    void getMissingOrArchivedOpportunityReturnsNotFound() throws Exception {
        UUID id = UUID.randomUUID();
        when(opportunityService.getById(id)).thenThrow(new OpportunityNotFoundException(id));
        mockMvc.perform(get("/api/opportunities/{id}", id))
                .andExpect(status().isNotFound());
    }

    @Test
    void updateAndChangeStageReturnOpportunity() throws Exception {
        UUID id = UUID.randomUUID();
        UUID companyId = UUID.randomUUID();
        when(opportunityService.update(eq(id), any())).thenReturn(response(id, companyId, null));
        mockMvc.perform(put("/api/opportunities/{id}", id).contentType(MediaType.APPLICATION_JSON)
                        .content(requestJson(companyId, null, "Deal", null, null)))
                .andExpect(status().isOk());

        when(opportunityService.changeStage(id, OpportunityStage.NEEDS_ANALYSIS))
                .thenReturn(response(id, companyId, null, OpportunityStage.NEEDS_ANALYSIS));
        mockMvc.perform(patch("/api/opportunities/{id}/stage", id).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"stage\":\"NEEDS_ANALYSIS\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.stage").value("NEEDS_ANALYSIS"));
    }

    @Test
    void changeStageRejectsMissingOrInvalidStage() throws Exception {
        UUID id = UUID.randomUUID();
        mockMvc.perform(patch("/api/opportunities/{id}/stage", id).contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest());
        mockMvc.perform(patch("/api/opportunities/{id}/stage", id).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"stage\":\"INVALID\"}"))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(opportunityService);
    }

    @Test
    void archiveReturnsNoContent() throws Exception {
        mockMvc.perform(delete("/api/opportunities/{id}", UUID.randomUUID()))
                .andExpect(status().isNoContent());
    }

    private String requestJson(UUID companyId, UUID contactId, String name, String amount, String currency) {
        StringBuilder json = new StringBuilder("{\"name\":\"").append(name).append("\"");
        if (companyId != null) json.append(",\"companyId\":\"").append(companyId).append("\"");
        if (contactId != null) json.append(",\"contactId\":\"").append(contactId).append("\"");
        if (amount != null) json.append(",\"amount\":").append(amount);
        if (currency != null) json.append(",\"currency\":\"").append(currency).append("\"");
        return json.append('}').toString();
    }

    private OpportunityResponse response(UUID id, UUID companyId, UUID contactId) {
        return response(id, companyId, contactId, OpportunityStage.QUALIFICATION);
    }

    private OpportunityResponse response(UUID id, UUID companyId, UUID contactId, OpportunityStage stage) {
        Instant now = Instant.parse("2026-01-01T00:00:00Z");
        return new OpportunityResponse(id, "Deal", null, null, null, stage, (LocalDate) null,
                companyId, contactId, now, now, false);
    }
}
