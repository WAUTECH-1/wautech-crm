package com.wautech.crm.company.dto;

import com.wautech.crm.company.entity.Company;

import java.time.Instant;
import java.util.UUID;

public record CompanyResponse(
        UUID id,
        String name,
        String website,
        String industry,
        String phone,
        String email,
        String status,
        Instant createdAt,
        Instant updatedAt,
        boolean archived
) {
    public static CompanyResponse from(Company company) {
        return new CompanyResponse(company.getId(), company.getName(), company.getWebsite(),
                company.getIndustry(), company.getPhone(), company.getEmail(), company.getStatus(),
                company.getCreatedAt(), company.getUpdatedAt(), company.isArchived());
    }
}
