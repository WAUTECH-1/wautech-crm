package com.wautech.crm.opportunity.dto;

import com.wautech.crm.opportunity.entity.OpportunityStage;
import jakarta.validation.constraints.NotNull;

public record OpportunityStageRequest(@NotNull OpportunityStage stage) {
}
