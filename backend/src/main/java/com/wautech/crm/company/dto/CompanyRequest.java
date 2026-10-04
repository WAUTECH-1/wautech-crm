package com.wautech.crm.company.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CompanyRequest(
        @NotBlank @Size(max = 200) String name,
        @Size(max = 2048) String website,
        @Size(max = 120) String industry,
        @Size(max = 40) String phone,
        @Email @Size(max = 254) String email,
        @Size(max = 40) String status
) {
    public String resolvedStatus() {
        return status == null || status.isBlank() ? "ACTIVE" : status.trim();
    }
}
