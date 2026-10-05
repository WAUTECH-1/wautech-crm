package com.wautech.crm.activity.dto;

import com.wautech.crm.activity.entity.Activity;
import com.wautech.crm.activity.entity.ActivityType;

import java.time.Instant;
import java.util.UUID;

public record ActivityResponse(
        UUID id,
        UUID companyId,
        UUID contactId,
        UUID leadId,
        UUID opportunityId,
        ActivityType type,
        String subject,
        String description,
        Instant occurredAt,
        Instant createdAt,
        Instant updatedAt,
        boolean archived
) {
    public static ActivityResponse from(Activity activity) {
        return new ActivityResponse(activity.getId(),
                activity.getCompany() == null ? null : activity.getCompany().getId(),
                activity.getContact() == null ? null : activity.getContact().getId(),
                activity.getLead() == null ? null : activity.getLead().getId(),
                activity.getOpportunity() == null ? null : activity.getOpportunity().getId(),
                activity.getType(), activity.getSubject(), activity.getDescription(), activity.getOccurredAt(),
                activity.getCreatedAt(), activity.getUpdatedAt(), activity.isArchived());
    }
}
