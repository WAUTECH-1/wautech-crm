package com.wautech.crm.pipeline.service;

import com.wautech.crm.opportunity.entity.Opportunity;
import com.wautech.crm.opportunity.entity.OpportunityStage;
import com.wautech.crm.opportunity.repository.OpportunityRepository;
import com.wautech.crm.pipeline.dto.PipelineResponse;
import com.wautech.crm.pipeline.dto.PipelineStageSummary;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class PipelineService {
    private final OpportunityRepository opportunityRepository;

    public PipelineService(OpportunityRepository opportunityRepository) {
        this.opportunityRepository = opportunityRepository;
    }

    @Transactional(readOnly = true)
    public PipelineResponse getPipeline(UUID companyId, UUID contactId) {
        Map<OpportunityStage, StageTotals> totalsByStage = new EnumMap<>(OpportunityStage.class);
        for (OpportunityStage stage : OpportunityStage.values()) {
            totalsByStage.put(stage, new StageTotals());
        }

        List<Opportunity> opportunities = opportunityRepository.findActive(companyId, contactId, null);
        for (Opportunity opportunity : opportunities) {
            totalsByStage.get(opportunity.getStage()).add(opportunity);
        }

        List<PipelineStageSummary> stages = List.of(OpportunityStage.values()).stream()
                .map(stage -> totalsByStage.get(stage).toSummary(stage))
                .toList();
        return new PipelineResponse(stages);
    }

    private static final class StageTotals {
        private long opportunityCount;
        private final Map<String, BigDecimal> totalsByCurrency = new LinkedHashMap<>();

        private void add(Opportunity opportunity) {
            opportunityCount++;
            if (opportunity.getAmount() != null && opportunity.getCurrency() != null) {
                totalsByCurrency.merge(opportunity.getCurrency(), opportunity.getAmount(), BigDecimal::add);
            }
        }

        private PipelineStageSummary toSummary(OpportunityStage stage) {
            return new PipelineStageSummary(stage, opportunityCount, Map.copyOf(totalsByCurrency));
        }
    }
}
