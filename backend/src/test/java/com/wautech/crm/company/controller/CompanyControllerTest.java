package com.wautech.crm.company.controller;

import com.wautech.crm.company.dto.CompanyResponse;
import com.wautech.crm.company.service.CompanyNotFoundException;
import com.wautech.crm.company.service.CompanyService;
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

@WebMvcTest(CompanyController.class)
class CompanyControllerTest {
    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private CompanyService companyService;

    @Test
    void createReturnsCompanyResponse() throws Exception {
        UUID id = UUID.randomUUID();
        when(companyService.create(any())).thenReturn(response(id, "Acme"));

        mockMvc.perform(post("/api/companies")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Acme\",\"email\":\"hello@acme.example\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(id.toString()))
                .andExpect(jsonPath("$.name").value("Acme"))
                .andExpect(jsonPath("$.archived").value(false));
    }

    @Test
    void createRejectsMissingName() throws Exception {
        mockMvc.perform(post("/api/companies")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"  \"}"))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(companyService);
    }

    @Test
    void createRejectsInvalidEmail() throws Exception {
        mockMvc.perform(post("/api/companies")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Acme\",\"email\":\"not-an-email\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void listReturnsCompaniesFromService() throws Exception {
        when(companyService.listActive()).thenReturn(List.of(response(UUID.randomUUID(), "Acme")));

        mockMvc.perform(get("/api/companies"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].name").value("Acme"));
    }

    @Test
    void getMissingCompanyReturnsNotFound() throws Exception {
        UUID id = UUID.randomUUID();
        when(companyService.getById(id)).thenThrow(new CompanyNotFoundException(id));

        mockMvc.perform(get("/api/companies/{id}", id))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.detail").value("Company with ID '" + id + "' was not found"));
    }

    @Test
    void updateReturnsUpdatedCompany() throws Exception {
        UUID id = UUID.randomUUID();
        when(companyService.update(eq(id), any())).thenReturn(response(id, "Updated"));

        mockMvc.perform(put("/api/companies/{id}", id)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Updated\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Updated"));
    }

    @Test
    void archiveReturnsNoContent() throws Exception {
        mockMvc.perform(delete("/api/companies/{id}", UUID.randomUUID()))
                .andExpect(status().isNoContent());
    }

    private CompanyResponse response(UUID id, String name) {
        Instant now = Instant.parse("2026-01-01T00:00:00Z");
        return new CompanyResponse(id, name, null, null, null, null, "ACTIVE", now, now, false);
    }
}
