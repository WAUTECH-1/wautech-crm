package com.wautech.crm.task.controller;

import com.wautech.crm.company.service.CompanyNotFoundException;
import com.wautech.crm.task.dto.TaskResponse;
import com.wautech.crm.task.entity.IllegalTaskStatusTransitionException;
import com.wautech.crm.task.entity.TaskPriority;
import com.wautech.crm.task.entity.TaskStatus;
import com.wautech.crm.task.service.TaskNotFoundException;
import com.wautech.crm.task.service.TaskService;
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

@WebMvcTest(TaskController.class)
class TaskControllerTest {
    @Autowired private MockMvc mockMvc;
    @MockitoBean private TaskService taskService;

    @Test
    void createsTaskWithDefaultsFromService() throws Exception {
        UUID companyId = UUID.randomUUID();
        UUID id = UUID.randomUUID();
        when(taskService.create(any())).thenReturn(response(id, companyId, TaskStatus.OPEN, TaskPriority.NORMAL, false));

        mockMvc.perform(post("/api/tasks").contentType(MediaType.APPLICATION_JSON)
                        .content(requestJson(companyId, null, null, null, "Prepare proposal", null, null)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(id.toString()))
                .andExpect(jsonPath("$.status").value("OPEN"))
                .andExpect(jsonPath("$.priority").value("NORMAL"))
                .andExpect(jsonPath("$.completedAt").value(org.hamcrest.Matchers.nullValue()));
    }

    @Test
    void validatesTitleDescriptionAndAtLeastOneParent() throws Exception {
        mockMvc.perform(post("/api/tasks").contentType(MediaType.APPLICATION_JSON)
                        .content(requestJson(UUID.randomUUID(), null, null, null, " ", null, null)))
                .andExpect(status().isBadRequest());
        mockMvc.perform(post("/api/tasks").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"companyId\":\"" + UUID.randomUUID() + "\",\"title\":\"" + "x".repeat(201) + "\"}"))
                .andExpect(status().isBadRequest());
        mockMvc.perform(post("/api/tasks").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"companyId\":\"" + UUID.randomUUID() + "\",\"title\":\"Task\",\"description\":\"" + "x".repeat(10001) + "\"}"))
                .andExpect(status().isBadRequest());
        mockMvc.perform(post("/api/tasks").contentType(MediaType.APPLICATION_JSON)
                        .content(requestJson(null, null, null, null, "Task", null, null)))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(taskService);
    }

    @Test
    void rejectsInvalidPriorityValues() throws Exception {
        mockMvc.perform(post("/api/tasks").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"companyId\":\"" + UUID.randomUUID() + "\",\"title\":\"Task\",\"priority\":\"CRITICAL\"}"))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(taskService);
    }

    @Test
    void mapsMissingParentsAndTasksToNotFound() throws Exception {
        UUID companyId = UUID.randomUUID();
        when(taskService.create(any())).thenThrow(new CompanyNotFoundException(companyId));
        mockMvc.perform(post("/api/tasks").contentType(MediaType.APPLICATION_JSON)
                        .content(requestJson(companyId, null, null, null, "Call customer", null, null)))
                .andExpect(status().isNotFound());

        UUID taskId = UUID.randomUUID();
        when(taskService.getById(taskId)).thenThrow(new TaskNotFoundException(taskId));
        mockMvc.perform(get("/api/tasks/{id}", taskId)).andExpect(status().isNotFound());
    }

    @Test
    void retrievesAndUpdatesTaskWithoutStatusOrCompletionFields() throws Exception {
        UUID id = UUID.randomUUID();
        UUID companyId = UUID.randomUUID();
        when(taskService.getById(id)).thenReturn(response(id, companyId, TaskStatus.IN_PROGRESS, TaskPriority.HIGH, false));
        when(taskService.update(eq(id), any())).thenReturn(response(id, companyId, TaskStatus.IN_PROGRESS, TaskPriority.URGENT, false));

        mockMvc.perform(get("/api/tasks/{id}", id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("IN_PROGRESS"));
        mockMvc.perform(put("/api/tasks/{id}", id).contentType(MediaType.APPLICATION_JSON)
                        .content(requestJson(companyId, null, null, null, "Updated task", "details", "URGENT")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("IN_PROGRESS"))
                .andExpect(jsonPath("$.priority").value("URGENT"));
    }

    @Test
    void changesStatusThroughDedicatedEndpoint() throws Exception {
        UUID id = UUID.randomUUID();
        when(taskService.changeStatus(id, TaskStatus.COMPLETED))
                .thenReturn(response(id, UUID.randomUUID(), TaskStatus.COMPLETED, TaskPriority.NORMAL, false));

        mockMvc.perform(patch("/api/tasks/{id}/status", id).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"COMPLETED\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("COMPLETED"));

        mockMvc.perform(patch("/api/tasks/{id}/status", id).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"UNKNOWN\"}"))
                .andExpect(status().isBadRequest());
        when(taskService.changeStatus(id, TaskStatus.CANCELLED))
                .thenThrow(new IllegalTaskStatusTransitionException(TaskStatus.COMPLETED, TaskStatus.CANCELLED));
        mockMvc.perform(patch("/api/tasks/{id}/status", id).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"CANCELLED\"}"))
                .andExpect(status().isBadRequest());
        verify(taskService).changeStatus(id, TaskStatus.COMPLETED);
    }

    @Test
    void listForwardsAllSupportedFiltersTogetherAndIndividually() throws Exception {
        UUID companyId = UUID.randomUUID();
        UUID contactId = UUID.randomUUID();
        UUID leadId = UUID.randomUUID();
        UUID opportunityId = UUID.randomUUID();
        Instant dueBefore = Instant.parse("2026-11-01T00:00:00Z");
        Instant dueAfter = Instant.parse("2026-10-01T00:00:00Z");
        when(taskService.listActive(companyId, contactId, leadId, opportunityId, TaskStatus.OPEN,
                TaskPriority.HIGH, dueBefore, dueAfter, true)).thenReturn(List.of());
        when(taskService.listActive(companyId, null, null, null, null, null, null, null, null)).thenReturn(List.of());
        when(taskService.listActive(null, contactId, null, null, null, null, null, null, null)).thenReturn(List.of());
        when(taskService.listActive(null, null, leadId, null, null, null, null, null, null)).thenReturn(List.of());
        when(taskService.listActive(null, null, null, opportunityId, null, null, null, null, null)).thenReturn(List.of());
        when(taskService.listActive(null, null, null, null, TaskStatus.COMPLETED, null, null, null, null)).thenReturn(List.of());
        when(taskService.listActive(null, null, null, null, null, TaskPriority.URGENT, null, null, null)).thenReturn(List.of());
        when(taskService.listActive(null, null, null, null, null, null, dueBefore, null, null)).thenReturn(List.of());
        when(taskService.listActive(null, null, null, null, null, null, null, dueAfter, null)).thenReturn(List.of());
        when(taskService.listActive(null, null, null, null, null, null, null, null, false)).thenReturn(List.of());

        mockMvc.perform(get("/api/tasks").param("companyId", companyId.toString())
                        .param("contactId", contactId.toString()).param("leadId", leadId.toString())
                        .param("opportunityId", opportunityId.toString()).param("status", "OPEN")
                        .param("priority", "HIGH").param("dueBefore", dueBefore.toString())
                        .param("dueAfter", dueAfter.toString()).param("overdue", "true"))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/tasks").param("companyId", companyId.toString())).andExpect(status().isOk());
        mockMvc.perform(get("/api/tasks").param("contactId", contactId.toString())).andExpect(status().isOk());
        mockMvc.perform(get("/api/tasks").param("leadId", leadId.toString())).andExpect(status().isOk());
        mockMvc.perform(get("/api/tasks").param("opportunityId", opportunityId.toString())).andExpect(status().isOk());
        mockMvc.perform(get("/api/tasks").param("status", "COMPLETED")).andExpect(status().isOk());
        mockMvc.perform(get("/api/tasks").param("priority", "URGENT")).andExpect(status().isOk());
        mockMvc.perform(get("/api/tasks").param("dueBefore", dueBefore.toString())).andExpect(status().isOk());
        mockMvc.perform(get("/api/tasks").param("dueAfter", dueAfter.toString())).andExpect(status().isOk());
        mockMvc.perform(get("/api/tasks").param("overdue", "false")).andExpect(status().isOk());

        verify(taskService).listActive(companyId, contactId, leadId, opportunityId, TaskStatus.OPEN,
                TaskPriority.HIGH, dueBefore, dueAfter, true);
        verify(taskService).listActive(companyId, null, null, null, null, null, null, null, null);
        verify(taskService).listActive(null, contactId, null, null, null, null, null, null, null);
        verify(taskService).listActive(null, null, leadId, null, null, null, null, null, null);
        verify(taskService).listActive(null, null, null, opportunityId, null, null, null, null, null);
        verify(taskService).listActive(null, null, null, null, TaskStatus.COMPLETED, null, null, null, null);
        verify(taskService).listActive(null, null, null, null, null, TaskPriority.URGENT, null, null, null);
        verify(taskService).listActive(null, null, null, null, null, null, dueBefore, null, null);
        verify(taskService).listActive(null, null, null, null, null, null, null, dueAfter, null);
        verify(taskService).listActive(null, null, null, null, null, null, null, null, false);
    }

    @Test
    void listForwardsSearchAndSortAlongsideTaskFilters() throws Exception {
        when(taskService.listActive(null, null, null, null, TaskStatus.OPEN, null,
                null, null, null, "proposal", "title", "desc")).thenReturn(List.of());
        mockMvc.perform(get("/api/tasks").param("status", "OPEN").param("search", "proposal")
                        .param("sortBy", "title").param("sortDirection", "desc"))
                .andExpect(status().isOk());
        verify(taskService).listActive(null, null, null, null, TaskStatus.OPEN, null,
                null, null, null, "proposal", "title", "desc");
    }

    @Test
    void invalidListFiltersReturnBadRequest() throws Exception {
        mockMvc.perform(get("/api/tasks").param("companyId", "bad-id")).andExpect(status().isBadRequest());
        mockMvc.perform(get("/api/tasks").param("status", "DONE")).andExpect(status().isBadRequest());
        mockMvc.perform(get("/api/tasks").param("priority", "CRITICAL")).andExpect(status().isBadRequest());
        mockMvc.perform(get("/api/tasks").param("dueBefore", "not-a-date")).andExpect(status().isBadRequest());
        mockMvc.perform(get("/api/tasks").param("overdue", "sometimes")).andExpect(status().isBadRequest());
    }

    @Test
    void archiveReturnsNoContent() throws Exception {
        mockMvc.perform(delete("/api/tasks/{id}", UUID.randomUUID())).andExpect(status().isNoContent());
    }

    private String requestJson(UUID companyId, UUID contactId, UUID leadId, UUID opportunityId,
                               String title, String description, String priority) {
        StringBuilder json = new StringBuilder("{");
        appendUuid(json, "companyId", companyId);
        appendUuid(json, "contactId", contactId);
        appendUuid(json, "leadId", leadId);
        appendUuid(json, "opportunityId", opportunityId);
        if (title != null) json.append("\"title\":\"").append(title).append("\",");
        if (description != null) json.append("\"description\":\"").append(description).append("\",");
        if (priority != null) json.append("\"priority\":\"").append(priority).append("\",");
        if (json.charAt(json.length() - 1) == ',') json.setLength(json.length() - 1);
        return json.append('}').toString();
    }

    private void appendUuid(StringBuilder json, String field, UUID value) {
        if (value != null) json.append('"').append(field).append("\":\"").append(value).append("\",");
    }

    private TaskResponse response(UUID id, UUID companyId, TaskStatus status, TaskPriority priority, boolean overdue) {
        Instant now = Instant.parse("2026-10-01T10:00:00Z");
        return new TaskResponse(id, companyId, null, null, null, "Prepare proposal", "Details", status, priority,
                now.plusSeconds(3600), status == TaskStatus.COMPLETED ? now : null,
                now.minusSeconds(3600), now, false, overdue);
    }
}
