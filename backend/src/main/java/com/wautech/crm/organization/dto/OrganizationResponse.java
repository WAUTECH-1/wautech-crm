package com.wautech.crm.organization.dto;

import com.wautech.crm.organization.entity.Organization;

import java.time.Instant;
import java.util.UUID;

public record OrganizationResponse(UUID id, String name, Instant createdAt, Instant updatedAt, boolean archived) {
    public static OrganizationResponse from(Organization organization) {
        return new OrganizationResponse(organization.getId(), organization.getName(), organization.getCreatedAt(),
                organization.getUpdatedAt(), organization.isArchived());
    }
}
