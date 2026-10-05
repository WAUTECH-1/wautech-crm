package com.wautech.crm.lead.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.util.UUID;

public record LeadRequest(
        UUID companyId,
        @NotBlank @Size(max = 100) String firstName,
        @NotBlank @Size(max = 100) String lastName,
        @Email @Size(max = 254) String email,
        @Size(max = 40) String phone,
        @Size(max = 120) String jobTitle
) {
}
