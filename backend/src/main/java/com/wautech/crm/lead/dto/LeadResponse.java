package com.wautech.crm.lead.dto;

import com.wautech.crm.lead.entity.Lead;
import com.wautech.crm.lead.entity.LeadStatus;

import java.time.Instant;
import java.util.UUID;

public record LeadResponse(UUID id, UUID companyId, String firstName, String lastName, String email,
                           String phone, String jobTitle, LeadStatus status, Instant createdAt,
                           Instant updatedAt, boolean archived) {
    public static LeadResponse from(Lead lead) {
        UUID companyId = lead.getCompany() == null ? null : lead.getCompany().getId();
        return new LeadResponse(lead.getId(), companyId, lead.getFirstName(), lead.getLastName(),
                lead.getEmail(), lead.getPhone(), lead.getJobTitle(), lead.getStatus(),
                lead.getCreatedAt(), lead.getUpdatedAt(), lead.isArchived());
    }
}
