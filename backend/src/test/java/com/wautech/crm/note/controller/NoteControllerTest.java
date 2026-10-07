package com.wautech.crm.note.controller;

import com.wautech.crm.note.dto.NoteResponse;
import com.wautech.crm.note.service.NoteNotFoundException;
import com.wautech.crm.note.service.NoteService;
import com.wautech.crm.company.service.CompanyNotFoundException;
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

@WebMvcTest(NoteController.class)
class NoteControllerTest {
    @Autowired private MockMvc mockMvc;
    @MockitoBean private NoteService noteService;

    @Test
    void createsNoteAndReturns201() throws Exception {
        UUID id = UUID.randomUUID();
        UUID companyId = UUID.randomUUID();
        when(noteService.create(any())).thenReturn(response(id, companyId, null, null, null, "Title"));

        mockMvc.perform(post("/api/notes").contentType(MediaType.APPLICATION_JSON)
                        .content(requestJson(companyId, null, null, null, "  Title  ", "Plain text")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(id.toString()))
                .andExpect(jsonPath("$.companyId").value(companyId.toString()))
                .andExpect(jsonPath("$.title").value("Title"));
    }

    @Test
    void rejectsInvalidFieldsAndZeroOrMultipleParents() throws Exception {
        UUID companyId = UUID.randomUUID();
        UUID contactId = UUID.randomUUID();
        mockMvc.perform(post("/api/notes").contentType(MediaType.APPLICATION_JSON)
                        .content(requestJson(null, null, null, null, "Title", "Body")))
                .andExpect(status().isBadRequest());
        mockMvc.perform(post("/api/notes").contentType(MediaType.APPLICATION_JSON)
                        .content(requestJson(companyId, contactId, null, null, "Title", "Body")))
                .andExpect(status().isBadRequest());
        mockMvc.perform(post("/api/notes").contentType(MediaType.APPLICATION_JSON)
                        .content(requestJson(companyId, null, null, null, " ", "Body")))
                .andExpect(status().isBadRequest());
        mockMvc.perform(post("/api/notes").contentType(MediaType.APPLICATION_JSON)
                        .content(requestJson(companyId, null, null, null, "Title", " ")))
                .andExpect(status().isBadRequest());
        mockMvc.perform(post("/api/notes").contentType(MediaType.APPLICATION_JSON)
                        .content(requestJson(companyId, null, null, null, "x".repeat(201), "Body")))
                .andExpect(status().isBadRequest());
        mockMvc.perform(post("/api/notes").contentType(MediaType.APPLICATION_JSON)
                        .content(requestJson(companyId, null, null, null, "Title", "x".repeat(10001))))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(noteService);
    }

    @Test
    void mapsMissingParentAndArchivedOrMissingNoteTo404() throws Exception {
        UUID companyId = UUID.randomUUID();
        when(noteService.create(any())).thenThrow(new CompanyNotFoundException(companyId));
        mockMvc.perform(post("/api/notes").contentType(MediaType.APPLICATION_JSON)
                        .content(requestJson(companyId, null, null, null, "Title", "Body")))
                .andExpect(status().isNotFound());

        UUID id = UUID.randomUUID();
        when(noteService.getById(id)).thenThrow(new NoteNotFoundException(id));
        mockMvc.perform(get("/api/notes/{id}", id)).andExpect(status().isNotFound());
    }

    @Test
    void readsAndUpdatesNote() throws Exception {
        UUID id = UUID.randomUUID();
        UUID companyId = UUID.randomUUID();
        when(noteService.getById(id)).thenReturn(response(id, companyId, null, null, null, "Title"));
        when(noteService.update(eq(id), any())).thenReturn(response(id, companyId, null, null, null, "Updated"));

        mockMvc.perform(get("/api/notes/{id}", id)).andExpect(status().isOk()).andExpect(jsonPath("$.title").value("Title"));
        mockMvc.perform(put("/api/notes/{id}", id).contentType(MediaType.APPLICATION_JSON)
                        .content(requestJson(companyId, null, null, null, "Updated", "Updated body")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.title").value("Updated"));
    }

    @Test
    void listForwardsAllParentFiltersTogether() throws Exception {
        UUID companyId = UUID.randomUUID();
        UUID contactId = UUID.randomUUID();
        UUID leadId = UUID.randomUUID();
        UUID opportunityId = UUID.randomUUID();
        when(noteService.listActive(companyId, contactId, leadId, opportunityId)).thenReturn(List.of());

        mockMvc.perform(get("/api/notes").param("companyId", companyId.toString())
                        .param("contactId", contactId.toString()).param("leadId", leadId.toString())
                        .param("opportunityId", opportunityId.toString()))
                .andExpect(status().isOk()).andExpect(content().json("[]"));
        verify(noteService).listActive(companyId, contactId, leadId, opportunityId);
    }

    @Test
    void rejectsInvalidFilterId() throws Exception {
        mockMvc.perform(get("/api/notes").param("companyId", "bad-uuid")).andExpect(status().isBadRequest());
    }

    @Test
    void deleteReturnsNoContent() throws Exception {
        mockMvc.perform(delete("/api/notes/{id}", UUID.randomUUID())).andExpect(status().isNoContent());
    }

    private String requestJson(UUID companyId, UUID contactId, UUID leadId, UUID opportunityId, String title, String body) {
        StringBuilder json = new StringBuilder("{");
        appendUuid(json, "companyId", companyId);
        appendUuid(json, "contactId", contactId);
        appendUuid(json, "leadId", leadId);
        appendUuid(json, "opportunityId", opportunityId);
        json.append("\"title\":\"").append(title).append("\",\"body\":\"").append(body).append("\"");
        return json.append('}').toString();
    }

    private void appendUuid(StringBuilder json, String name, UUID value) {
        if (value != null) json.append('"').append(name).append("\":\"").append(value).append("\",");
    }

    private NoteResponse response(UUID id, UUID companyId, UUID contactId, UUID leadId,
                                   UUID opportunityId, String title) {
        Instant now = Instant.parse("2026-01-01T10:00:00Z");
        return new NoteResponse(id, companyId, contactId, leadId, opportunityId, title,
                "Plain text", now, now, false);
    }
}
