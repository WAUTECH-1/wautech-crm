package com.wautech.crm.pipeline.dto;

import com.wautech.crm.opportunity.entity.OpportunityStage;

import java.math.BigDecimal;
import java.util.Map;

public record PipelineStageSummary(
        OpportunityStage stage,
        long opportunityCount,
        Map<String, BigDecimal> totalsByCurrency
) {
}
