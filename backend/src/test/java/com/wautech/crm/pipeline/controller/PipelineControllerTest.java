package com.wautech.crm.pipeline.controller;

import com.wautech.crm.opportunity.entity.OpportunityStage;
import com.wautech.crm.pipeline.dto.PipelineResponse;
import com.wautech.crm.pipeline.dto.PipelineStageSummary;
import com.wautech.crm.pipeline.service.PipelineService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(PipelineController.class)
class PipelineControllerTest {
    @Autowired private MockMvc mockMvc;
    @MockitoBean private PipelineService pipelineService;

    @Test
    void returnsPipelineStageCountsAndCurrencyTotals() throws Exception {
        when(pipelineService.getPipeline(null, null)).thenReturn(new PipelineResponse(List.of(
                new PipelineStageSummary(OpportunityStage.QUALIFICATION, 2,
                        Map.of("USD", new BigDecimal("125.50"), "EUR", new BigDecimal("40"))))));

        mockMvc.perform(get("/api/pipeline"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.stages[0].stage").value("QUALIFICATION"))
                .andExpect(jsonPath("$.stages[0].opportunityCount").value(2))
                .andExpect(jsonPath("$.stages[0].totalsByCurrency.USD").value(125.5))
                .andExpect(jsonPath("$.stages[0].totalsByCurrency.EUR").value(40));

        verify(pipelineService).getPipeline(null, null);
    }

    @Test
    void forwardsCompanyAndContactFiltersTogether() throws Exception {
        UUID companyId = UUID.randomUUID();
        UUID contactId = UUID.randomUUID();
        when(pipelineService.getPipeline(companyId, contactId)).thenReturn(new PipelineResponse(List.of()));

        mockMvc.perform(get("/api/pipeline").param("companyId", companyId.toString())
                        .param("contactId", contactId.toString()))
                .andExpect(status().isOk());

        verify(pipelineService).getPipeline(companyId, contactId);
    }

    @Test
    void forwardsCompanyAndContactFiltersIndividually() throws Exception {
        UUID companyId = UUID.randomUUID();
        UUID contactId = UUID.randomUUID();
        when(pipelineService.getPipeline(companyId, null)).thenReturn(new PipelineResponse(List.of()));
        when(pipelineService.getPipeline(null, contactId)).thenReturn(new PipelineResponse(List.of()));

        mockMvc.perform(get("/api/pipeline").param("companyId", companyId.toString()))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/pipeline").param("contactId", contactId.toString()))
                .andExpect(status().isOk());

        verify(pipelineService).getPipeline(companyId, null);
        verify(pipelineService).getPipeline(null, contactId);
    }

    @Test
    void rejectsInvalidFilterIds() throws Exception {
        mockMvc.perform(get("/api/pipeline").param("companyId", "invalid"))
                .andExpect(status().isBadRequest());
        mockMvc.perform(get("/api/pipeline").param("contactId", "invalid"))
                .andExpect(status().isBadRequest());
    }
}
