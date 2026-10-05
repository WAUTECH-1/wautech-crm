package com.wautech.crm.lead.dto;

import com.wautech.crm.lead.entity.LeadStatus;
import jakarta.validation.constraints.NotNull;

public record LeadStatusRequest(@NotNull LeadStatus status) {
}
