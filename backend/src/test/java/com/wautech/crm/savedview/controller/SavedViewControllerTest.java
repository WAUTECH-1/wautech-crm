package com.wautech.crm.savedview.controller;

import com.wautech.crm.platform.tenant.AuthenticatedOrganizationContext;
import com.wautech.crm.TestOrganization;
import com.wautech.crm.savedview.dto.SavedViewResponse;
import com.wautech.crm.savedview.entity.SavedViewResource;
import com.wautech.crm.savedview.service.SavedViewService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(SavedViewController.class)
class SavedViewControllerTest {
    @MockitoBean private AuthenticatedOrganizationContext organizationContext;
    @BeforeEach
    void organizationContextUsesTestOrganization() {
        org.mockito.Mockito.when(organizationContext.requireOrganizationId()).thenReturn(com.wautech.crm.TestOrganization.ID);
    }

    @Autowired MockMvc mockMvc;
    @MockitoBean SavedViewService service;

    @Test
    void createsAndRetrievesSavedViewsUsingDtoResponse() throws Exception {
        UUID id = UUID.randomUUID();
        var response = new SavedViewResponse(id, "Prospects", SavedViewResource.LEAD, 1,
                new com.fasterxml.jackson.databind.ObjectMapper().readTree("{\"search\":\"Acme\"}"),
                Instant.parse("2026-10-01T00:00:00Z"), Instant.parse("2026-10-01T00:00:00Z"));
        when(service.create(eq(TestOrganization.ID), any())).thenReturn(response);
        when(service.listActive(TestOrganization.ID)).thenReturn(List.of(response));

        mockMvc.perform(post("/api/saved-views").contentType("application/json")
                        .content("{\"name\":\"Prospects\",\"resource\":\"LEAD\",\"configurationVersion\":1," +
                                "\"configuration\":{\"search\":\"Acme\",\"filters\":{},\"sortBy\":\"createdAt\",\"sortDirection\":\"desc\"}}"))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.resource").value("LEAD"));
        mockMvc.perform(get("/api/saved-views")).andExpect(status().isOk())
                .andExpect(jsonPath("$[0].configuration.search").value("Acme"));
        verify(service).create(eq(TestOrganization.ID), any());
        verify(service).listActive(TestOrganization.ID);
    }

    @Test
    void deleteArchivesSavedView() throws Exception {
        UUID id = UUID.randomUUID();
        mockMvc.perform(delete("/api/saved-views/{id}", id)).andExpect(status().isNoContent());
        verify(service).archive(TestOrganization.ID, id);
    }
}
