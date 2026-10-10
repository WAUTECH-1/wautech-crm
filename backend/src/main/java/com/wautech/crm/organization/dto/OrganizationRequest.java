package com.wautech.crm.organization.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record OrganizationRequest(@NotBlank @Size(max = 200) String name) {
}
