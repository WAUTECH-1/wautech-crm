package com.wautech.crm.organization.dto;

import com.wautech.crm.organization.entity.MembershipStatus;
import jakarta.validation.constraints.NotNull;

public record MembershipStatusRequest(@NotNull MembershipStatus status) { }
