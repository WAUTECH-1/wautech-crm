package com.wautech.crm.contact.controller;

import com.wautech.crm.platform.tenant.AuthenticatedOrganizationContext;
import com.wautech.crm.TestOrganization;
import com.wautech.crm.company.service.CompanyNotFoundException;
import com.wautech.crm.contact.dto.ContactResponse;
import com.wautech.crm.contact.service.ContactNotFoundException;
import com.wautech.crm.contact.service.ContactService;
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

import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(ContactController.class)
class ContactControllerTest {
    @BeforeEach
    void organizationContextUsesTestOrganization() {
        org.mockito.Mockito.when(organizationContext.requireOrganizationId()).thenReturn(com.wautech.crm.TestOrganization.ID);
    }

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private AuthenticatedOrganizationContext organizationContext;

    @MockitoBean
    private ContactService contactService;

    @Test
    void createReturnsContactResponse() throws Exception {
        UUID id = UUID.randomUUID();
        UUID companyId = UUID.randomUUID();
        when(contactService.create(eq(TestOrganization.ID), any())).thenReturn(response(id, companyId, "Ada", "Lovelace"));

        mockMvc.perform(post("/api/contacts")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestJson(companyId, "Ada", "Lovelace", "ada@example.com")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(id.toString()))
                .andExpect(jsonPath("$.companyId").value(companyId.toString()))
                .andExpect(jsonPath("$.firstName").value("Ada"))
                .andExpect(jsonPath("$.lastName").value("Lovelace"))
                .andExpect(jsonPath("$.archived").value(false));
    }

    @Test
    void createRejectsMissingNamesCompanyAndInvalidEmail() throws Exception {
        mockMvc.perform(post("/api/contacts")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"companyId\":\"" + UUID.randomUUID() + "\",\"firstName\":\" \"}"))
                .andExpect(status().isBadRequest());
        mockMvc.perform(post("/api/contacts")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestJson(UUID.randomUUID(), "Ada", " ", null)))
                .andExpect(status().isBadRequest());
        mockMvc.perform(post("/api/contacts")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"firstName\":\"Ada\",\"lastName\":\"Lovelace\"}"))
                .andExpect(status().isBadRequest());
        mockMvc.perform(post("/api/contacts")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestJson(UUID.randomUUID(), "Ada", "Lovelace", "invalid-email")))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(contactService);
    }

    @Test
    void createReturnsNotFoundForMissingCompany() throws Exception {
        UUID companyId = UUID.randomUUID();
        when(contactService.create(eq(TestOrganization.ID), any())).thenThrow(new CompanyNotFoundException(companyId));

        mockMvc.perform(post("/api/contacts")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestJson(companyId, "Ada", "Lovelace", null)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.detail").value("Company with ID '" + companyId + "' was not found"));
    }

    @Test
    void listReturnsContactsAndSupportsCompanyFilter() throws Exception {
        UUID companyId = UUID.randomUUID();
        when(contactService.listActive(TestOrganization.ID, companyId)).thenReturn(List.of(response(UUID.randomUUID(), companyId, "Ada", "Lovelace")));

        mockMvc.perform(get("/api/contacts").param("companyId", companyId.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].firstName").value("Ada"));

        verify(contactService).listActive(TestOrganization.ID, companyId);
    }

    @Test
    void listCombinesSearchAndSortWithExistingCompanyFilter() throws Exception {
        UUID companyId = UUID.randomUUID();
        when(contactService.listActive(TestOrganization.ID, companyId, "ada", "lastName", "desc")).thenReturn(List.of());
        mockMvc.perform(get("/api/contacts").param("companyId", companyId.toString())
                        .param("search", "ada").param("sortBy", "lastName").param("sortDirection", "desc"))
                .andExpect(status().isOk());
        verify(contactService).listActive(TestOrganization.ID, companyId, "ada", "lastName", "desc");
    }

    @Test
    void listRejectsInvalidCompanyFilter() throws Exception {
        mockMvc.perform(get("/api/contacts").param("companyId", "not-a-uuid"))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(contactService);
    }

    @Test
    void getMissingOrArchivedContactReturnsNotFound() throws Exception {
        UUID id = UUID.randomUUID();
        when(contactService.getById(TestOrganization.ID, id)).thenThrow(new ContactNotFoundException(id));

        mockMvc.perform(get("/api/contacts/{id}", id))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.detail").value("Contact with ID '" + id + "' was not found"));
    }

    @Test
    void updateReturnsUpdatedContact() throws Exception {
        UUID id = UUID.randomUUID();
        UUID companyId = UUID.randomUUID();
        when(contactService.update(eq(TestOrganization.ID), eq(id), any())).thenReturn(response(id, companyId, "Augusta", "King"));

        mockMvc.perform(put("/api/contacts/{id}", id)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestJson(companyId, "Augusta", "King", null)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.firstName").value("Augusta"))
                .andExpect(jsonPath("$.lastName").value("King"));
    }

    @Test
    void archiveReturnsNoContent() throws Exception {
        mockMvc.perform(delete("/api/contacts/{id}", UUID.randomUUID()))
                .andExpect(status().isNoContent());
    }

    private String requestJson(UUID companyId, String firstName, String lastName, String email) {
        String emailField = email == null ? "" : ",\"email\":\"" + email + "\"";
        return "{\"companyId\":\"" + companyId + "\",\"firstName\":\"" + firstName
                + "\",\"lastName\":\"" + lastName + "\"" + emailField + "}";
    }

    private ContactResponse response(UUID id, UUID companyId, String firstName, String lastName) {
        Instant now = Instant.parse("2026-01-01T00:00:00Z");
        return new ContactResponse(id, companyId, firstName, lastName, null, null, null,
                "ACTIVE", now, now, false);
    }
}
