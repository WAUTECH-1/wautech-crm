package com.wautech.crm.pipeline.service;

import com.wautech.crm.opportunity.entity.Opportunity;
import com.wautech.crm.opportunity.entity.OpportunityStage;
import com.wautech.crm.opportunity.repository.OpportunityRepository;
import com.wautech.crm.pipeline.dto.PipelineResponse;
import com.wautech.crm.pipeline.dto.PipelineStageSummary;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PipelineServiceTest {
    @Mock private OpportunityRepository opportunityRepository;
    @InjectMocks private PipelineService pipelineService;

    @Test
    void returnsEveryStageIncludingStagesWithNoActiveOpportunities() {
        when(opportunityRepository.findActive(null, null, null)).thenReturn(List.of(
                opportunity("Qualification", OpportunityStage.QUALIFICATION, "100", "USD"),
                opportunity("Proposal", OpportunityStage.PROPOSAL, "200", "USD")));

        PipelineResponse response = pipelineService.getPipeline(null, null);

        assertEquals(List.of(OpportunityStage.values()), response.stages().stream()
                .map(PipelineStageSummary::stage).toList());
        assertEquals(6, response.stages().size());
        assertEquals(0, summary(response, OpportunityStage.NEEDS_ANALYSIS).opportunityCount());
        assertEquals(0, summary(response, OpportunityStage.NEGOTIATION).opportunityCount());
        assertEquals(0, summary(response, OpportunityStage.CLOSED_WON).opportunityCount());
        assertEquals(0, summary(response, OpportunityStage.CLOSED_LOST).opportunityCount());
        assertTrue(summary(response, OpportunityStage.NEEDS_ANALYSIS).totalsByCurrency().isEmpty());
    }

    @Test
    void countsActiveOpportunitiesAndGroupsThemByCurrentStage() {
        when(opportunityRepository.findActive(null, null, null)).thenReturn(List.of(
                opportunity("A", OpportunityStage.QUALIFICATION, "10", "USD"),
                opportunity("B", OpportunityStage.QUALIFICATION, "20", "USD"),
                opportunity("C", OpportunityStage.NEGOTIATION, "30", "USD")));

        PipelineResponse response = pipelineService.getPipeline(null, null);

        assertEquals(2, summary(response, OpportunityStage.QUALIFICATION).opportunityCount());
        assertEquals(1, summary(response, OpportunityStage.NEGOTIATION).opportunityCount());
        assertEquals(0, summary(response, OpportunityStage.CLOSED_LOST).opportunityCount());
        verify(opportunityRepository).findActive(null, null, null);
    }

    @Test
    void sumsAmountsByCurrencyWithoutCombiningCurrencies() {
        when(opportunityRepository.findActive(null, null, null)).thenReturn(List.of(
                opportunity("USD 1", OpportunityStage.PROPOSAL, "125.25", "USD"),
                opportunity("USD 2", OpportunityStage.PROPOSAL, "74.75", "USD"),
                opportunity("EUR", OpportunityStage.PROPOSAL, "40000", "EUR")));

        PipelineStageSummary proposal = summary(pipelineService.getPipeline(null, null), OpportunityStage.PROPOSAL);

        assertEquals(new BigDecimal("200.00"), proposal.totalsByCurrency().get("USD"));
        assertEquals(new BigDecimal("40000"), proposal.totalsByCurrency().get("EUR"));
        assertEquals(2, proposal.totalsByCurrency().size());
    }

    @Test
    void countsOpportunitiesWithoutCompleteAmountAndCurrencyButDoesNotTotalThem() {
        when(opportunityRepository.findActive(null, null, null)).thenReturn(List.of(
                opportunity("No amount", OpportunityStage.QUALIFICATION, null, null),
                opportunity("No currency", OpportunityStage.QUALIFICATION, "50", null),
                opportunity("No amount but currency", OpportunityStage.QUALIFICATION, null, "USD")));

        PipelineStageSummary qualification = summary(pipelineService.getPipeline(null, null), OpportunityStage.QUALIFICATION);

        assertEquals(3, qualification.opportunityCount());
        assertTrue(qualification.totalsByCurrency().isEmpty());
    }

    @Test
    void usesCompanyAndContactFiltersTogetherAndExcludesArchivedRecordsThroughActiveQuery() {
        UUID companyId = UUID.randomUUID();
        UUID contactId = UUID.randomUUID();
        when(opportunityRepository.findActive(companyId, contactId, null)).thenReturn(List.of(
                opportunity("Active", OpportunityStage.QUALIFICATION, "10", "USD")));

        PipelineResponse response = pipelineService.getPipeline(companyId, contactId);

        assertEquals(1, summary(response, OpportunityStage.QUALIFICATION).opportunityCount());
        verify(opportunityRepository).findActive(companyId, contactId, null);
    }

    @Test
    void reflectsAnOpportunitiesCurrentStage() {
        Opportunity opportunity = opportunity("Moving deal", OpportunityStage.QUALIFICATION, "15", "USD");
        opportunity.changeStage(OpportunityStage.NEEDS_ANALYSIS);
        when(opportunityRepository.findActive(null, null, null)).thenReturn(List.of(opportunity));

        PipelineResponse response = pipelineService.getPipeline(null, null);

        assertEquals(0, summary(response, OpportunityStage.QUALIFICATION).opportunityCount());
        assertEquals(1, summary(response, OpportunityStage.NEEDS_ANALYSIS).opportunityCount());
    }

    private PipelineStageSummary summary(PipelineResponse response, OpportunityStage stage) {
        return response.stages().stream().filter(item -> item.stage() == stage).findFirst().orElseThrow();
    }

    private Opportunity opportunity(String name, OpportunityStage stage, String amount, String currency) {
        return new Opportunity(name, null, amount == null ? null : new BigDecimal(amount), currency,
                stage, null, null, null);
    }
}
