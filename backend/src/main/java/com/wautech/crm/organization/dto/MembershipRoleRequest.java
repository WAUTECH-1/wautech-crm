package com.wautech.crm.organization.dto;

import com.wautech.crm.organization.entity.OrganizationRole;
import jakarta.validation.constraints.NotNull;

public record MembershipRoleRequest(@NotNull OrganizationRole role) { }
