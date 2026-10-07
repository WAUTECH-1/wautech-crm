package com.wautech.crm.task.dto;

import com.wautech.crm.task.entity.Task;
import com.wautech.crm.task.entity.TaskPriority;
import com.wautech.crm.task.entity.TaskStatus;

import java.time.Instant;
import java.util.UUID;

public record TaskResponse(
        UUID id,
        UUID companyId,
        UUID contactId,
        UUID leadId,
        UUID opportunityId,
        String title,
        String description,
        TaskStatus status,
        TaskPriority priority,
        Instant dueAt,
        Instant completedAt,
        Instant createdAt,
        Instant updatedAt,
        boolean archived,
        boolean overdue
) {
    public static TaskResponse from(Task task) {
        return from(task, Instant.now());
    }

    public static TaskResponse from(Task task, Instant now) {
        return new TaskResponse(task.getId(),
                task.getCompany() == null ? null : task.getCompany().getId(),
                task.getContact() == null ? null : task.getContact().getId(),
                task.getLead() == null ? null : task.getLead().getId(),
                task.getOpportunity() == null ? null : task.getOpportunity().getId(),
                task.getTitle(), task.getDescription(), task.getStatus(), task.getPriority(), task.getDueAt(),
                task.getCompletedAt(), task.getCreatedAt(), task.getUpdatedAt(), task.isArchived(),
                task.isOverdueAt(now));
    }
}
