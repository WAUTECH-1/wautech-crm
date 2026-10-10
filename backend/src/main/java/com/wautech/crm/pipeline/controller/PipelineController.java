package com.wautech.crm.pipeline.controller;

import com.wautech.crm.pipeline.dto.PipelineResponse;
import com.wautech.crm.pipeline.service.PipelineService;
import com.wautech.crm.platform.tenant.AuthenticatedOrganizationContext;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/pipeline")
public class PipelineController {
    private final PipelineService pipelineService;
    private final AuthenticatedOrganizationContext organizationContext;

    public PipelineController(PipelineService pipelineService, AuthenticatedOrganizationContext organizationContext) {
        this.pipelineService = pipelineService;
        this.organizationContext = organizationContext;
    }

    @GetMapping
    public PipelineResponse getPipeline(@RequestParam(required = false) UUID companyId,
                                        @RequestParam(required = false) UUID contactId) {
        return pipelineService.getPipeline(organizationContext.requireOrganizationId(), companyId, contactId);
    }
}
