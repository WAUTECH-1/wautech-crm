package com.wautech.crm.organization.dto;

import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public record OrganizationMembershipCreateRequest(@NotNull UUID userId) { }
