package com.wautech.crm.activity.dto;

import com.wautech.crm.activity.entity.ActivityType;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.util.UUID;

public record ActivityRequest(
        UUID companyId,
        UUID contactId,
        UUID leadId,
        UUID opportunityId,
        @NotNull ActivityType type,
        @NotBlank @Size(max = 200) String subject,
        @Size(max = 10000) String description,
        @NotNull Instant occurredAt
) {
    @AssertTrue(message = "At least one CRM record ID must be provided")
    public boolean isParentRecordProvided() {
        return companyId != null || contactId != null || leadId != null || opportunityId != null;
    }
}
