package com.wautech.crm.opportunity.dto;

import com.wautech.crm.opportunity.entity.Opportunity;
import com.wautech.crm.opportunity.entity.OpportunityStage;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

public record OpportunityResponse(
        UUID id,
        String name,
        String description,
        BigDecimal amount,
        String currency,
        OpportunityStage stage,
        LocalDate expectedCloseDate,
        UUID companyId,
        UUID contactId,
        Instant createdAt,
        Instant updatedAt,
        boolean archived
) {
    public static OpportunityResponse from(Opportunity opportunity) {
        return new OpportunityResponse(opportunity.getId(), opportunity.getName(), opportunity.getDescription(),
                opportunity.getAmount(), opportunity.getCurrency(), opportunity.getStage(),
                opportunity.getExpectedCloseDate(), opportunity.getCompany().getId(),
                opportunity.getContact() == null ? null : opportunity.getContact().getId(),
                opportunity.getCreatedAt(), opportunity.getUpdatedAt(), opportunity.isArchived());
    }
}
