package com.wautech.crm.pipeline.dto;

import java.util.List;

public record PipelineResponse(List<PipelineStageSummary> stages) {
}
