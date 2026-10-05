package com.wautech.crm.contact.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.util.UUID;

public record ContactRequest(
        @NotNull UUID companyId,
        @NotBlank @Size(max = 100) String firstName,
        @NotBlank @Size(max = 100) String lastName,
        @Email @Size(max = 254) String email,
        @Size(max = 40) String phone,
        @Size(max = 120) String jobTitle,
        @Size(max = 40) String status
) {
    public String resolvedStatus() {
        return status == null || status.isBlank() ? "ACTIVE" : status.trim();
    }
}
