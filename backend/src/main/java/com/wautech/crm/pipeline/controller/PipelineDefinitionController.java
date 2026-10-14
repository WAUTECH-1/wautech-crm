package com.wautech.crm.pipeline.controller;

import com.wautech.crm.pipeline.service.PipelineDefinitionService;
import com.wautech.crm.platform.tenant.AuthenticatedOrganizationContext;
import java.util.*;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api")
public class PipelineDefinitionController {
  private final PipelineDefinitionService service;
  private final AuthenticatedOrganizationContext context;

  public PipelineDefinitionController(
      PipelineDefinitionService s, AuthenticatedOrganizationContext c) {
    service = s;
    context = c;
  }

  public record Move(UUID pipelineId, String stageKey) {}

  @GetMapping("/pipeline-definitions")
  public List<PipelineDefinitionService.Response> list() {
    return service.list(context.requireOrganizationId());
  }

  @PostMapping("/pipeline-definitions")
  @ResponseStatus(HttpStatus.CREATED)
  public PipelineDefinitionService.Response create(
      @RequestBody PipelineDefinitionService.Request r) {
    return service.create(context.requireOrganizationId(), r);
  }

  @PutMapping("/pipeline-definitions/{id}")
  public PipelineDefinitionService.Response update(
      @PathVariable UUID id, @RequestBody PipelineDefinitionService.Request r) {
    return service.update(context.requireOrganizationId(), id, r);
  }

  @DeleteMapping("/pipeline-definitions/{id}")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  public void archive(@PathVariable UUID id) {
    service.archive(context.requireOrganizationId(), id);
  }

  @PatchMapping("/opportunities/{id}/pipeline-stage")
  public com.wautech.crm.opportunity.dto.OpportunityResponse move(
      @PathVariable UUID id, @RequestBody Move r) {
    if (r.pipelineId() == null || r.stageKey() == null)
      throw new com.wautech.crm.platform.search.InvalidListQueryException(
          "Pipeline and stage are required");
    return service.move(context.requireOrganizationId(), id, r.pipelineId(), r.stageKey());
  }
}
