package com.wautech.crm.task.dto;

import com.wautech.crm.task.entity.TaskPriority;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.util.UUID;

public record TaskRequest(
        UUID companyId,
        UUID contactId,
        UUID leadId,
        UUID opportunityId,
        @NotBlank @Size(max = 200) String title,
        @Size(max = 10000) String description,
        TaskPriority priority,
        Instant dueAt
) {
    @AssertTrue(message = "At least one CRM record ID must be provided")
    public boolean isParentRecordProvided() {
        return companyId != null || contactId != null || leadId != null || opportunityId != null;
    }
}
