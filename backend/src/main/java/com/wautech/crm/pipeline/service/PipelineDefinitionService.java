package com.wautech.crm.pipeline.service;

import com.wautech.crm.audit.AuditedMutation;
import com.wautech.crm.opportunity.dto.OpportunityResponse;
import com.wautech.crm.opportunity.entity.OpportunityStage;
import com.wautech.crm.opportunity.repository.OpportunityRepository;
import com.wautech.crm.opportunity.service.OpportunityNotFoundException;
import com.wautech.crm.organization.service.OrganizationService;
import com.wautech.crm.pipeline.entity.PipelineDefinition;
import com.wautech.crm.pipeline.repository.PipelineDefinitionRepository;
import com.wautech.crm.platform.search.InvalidListQueryException;
import java.util.*;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
@Transactional
public class PipelineDefinitionService {
  private final PipelineDefinitionRepository pipelines;
  private final OpportunityRepository deals;
  private final OrganizationService organizations;

  public PipelineDefinitionService(
      PipelineDefinitionRepository p, OpportunityRepository d, OrganizationService o) {
    pipelines = p;
    deals = d;
    organizations = o;
  }

  public record Request(String name, List<PipelineDefinition.Stage> stages) {}

  public record Response(UUID id, String name, List<PipelineDefinition.Stage> stages) {
    public static Response from(PipelineDefinition p) {
      return new Response(p.getId(), p.getName(), p.getStages());
    }
  }

  public static void validate(Request r) {
    if (r.name() == null
        || r.name().isBlank()
        || r.name().length() > 200
        || r.stages() == null
        || r.stages().size() < 3
        || r.stages().size() > 30)
      throw new InvalidListQueryException("Provide a name and 3–30 stages");
    var keys = new HashSet<String>();
    int open = 0, won = 0, lost = 0;
    for (var stage : r.stages()) {
      if (stage == null
          || stage.key() == null
          || !stage.key().matches("[a-z][a-z0-9_-]{0,49}")
          || !keys.add(stage.key())
          || stage.name() == null
          || stage.name().isBlank()
          || stage.name().length() > 100
          || stage.outcome() == null
          || !List.of("OPEN", "WON", "LOST").contains(stage.outcome()))
        throw new InvalidListQueryException("Invalid or duplicate stage");
      if (stage.outcome().equals("OPEN")) open++;
      if (stage.outcome().equals("WON")) won++;
      if (stage.outcome().equals("LOST")) lost++;
    }
    if (open < 1 || won != 1 || lost != 1)
      throw new InvalidListQueryException("Use open stages and exactly one won and lost stage");
  }

  private PipelineDefinition find(UUID org, UUID id) {
    return pipelines
        .findByIdAndOrganizationIdAndArchivedFalse(id, org)
        .orElseThrow(
            () -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Pipeline unavailable"));
  }

  @Transactional(readOnly = true)
  @PreAuthorize("@crmAuthorization.canView(#p0)")
  public List<Response> list(UUID org) {
    organizations.requireActiveOrganization(org);
    return pipelines.findByOrganizationIdAndArchivedFalseOrderByNameAsc(org).stream()
        .map(Response::from)
        .toList();
  }

  @PreAuthorize("@crmAuthorization.canManageOrganization(#p0)")
  @AuditedMutation(eventType = "PIPELINE_CREATED", targetType = "PIPELINE")
  public Response create(UUID org, Request r) {
    organizations.lockActiveOrganization(org);
    validate(r);
    if (pipelines.existsByOrganizationIdAndNameIgnoreCaseAndArchivedFalse(org, r.name().trim()))
      throw new InvalidListQueryException("A pipeline with this name already exists");
    return Response.from(pipelines.save(new PipelineDefinition(org, r.name().trim(), r.stages())));
  }

  @PreAuthorize("@crmAuthorization.canManageOrganization(#p0)")
  @AuditedMutation(eventType = "PIPELINE_UPDATED", targetType = "PIPELINE")
  public Response update(UUID org, UUID id, Request r) {
    organizations.lockActiveOrganization(org);
    validate(r);
    var p = find(org, id);
    if (!p.getName().equalsIgnoreCase(r.name().trim())
        && pipelines.existsByOrganizationIdAndNameIgnoreCaseAndArchivedFalse(org, r.name().trim()))
      throw new InvalidListQueryException("A pipeline with this name already exists");
    for (var old : p.getStages()) {
      var next = r.stages().stream().filter(s -> s.key().equals(old.key())).findFirst();
      if (deals.existsByOrganization_IdAndPipelineIdAndPipelineStageKeyAndArchivedFalse(
              org, id, old.key())
          && (next.isEmpty() || !next.get().outcome().equals(old.outcome())))
        throw new InvalidListQueryException(
            "Move deals before removing or changing an occupied stage outcome");
    }
    p.update(r.name().trim(), r.stages());
    return Response.from(pipelines.save(p));
  }

  @PreAuthorize("@crmAuthorization.canManageOrganization(#p0)")
  @AuditedMutation(eventType = "PIPELINE_ARCHIVED", targetType = "PIPELINE")
  public void archive(UUID org, UUID id) {
    organizations.lockActiveOrganization(org);
    var p = find(org, id);
    if (deals.existsByOrganization_IdAndPipelineIdAndArchivedFalse(org, id))
      throw new InvalidListQueryException("Move or archive deals before archiving this pipeline");
    p.archive();
    pipelines.save(p);
  }

  @PreAuthorize("@crmAuthorization.canWrite(#p0)")
  @AuditedMutation(eventType = "OPPORTUNITY_STAGE_CHANGED", targetType = "OPPORTUNITY")
  public OpportunityResponse move(UUID org, UUID dealId, UUID pipelineId, String stageKey) {
    organizations.lockActiveOrganization(org);
    var p = find(org, pipelineId);
    var next =
        p.getStages().stream()
            .filter(s -> s.key().equals(stageKey))
            .findFirst()
            .orElseThrow(() -> new InvalidListQueryException("Unknown stage"));
    var deal =
        deals
            .findByIdAndOrganization_IdAndArchivedFalse(dealId, org)
            .orElseThrow(() -> new OpportunityNotFoundException(dealId));
    if ((deal.getStage() == OpportunityStage.CLOSED_WON && !next.outcome().equals("WON"))
        || (deal.getStage() == OpportunityStage.CLOSED_LOST && !next.outcome().equals("LOST")))
      throw new InvalidListQueryException("Closed opportunities cannot be reopened");
    if (pipelineId.equals(deal.getPipelineId()) && next.outcome().equals("OPEN")) {
      var open =
          p.getStages().stream()
              .filter(s -> s.outcome().equals("OPEN"))
              .map(PipelineDefinition.Stage::key)
              .toList();
      int current = open.indexOf(deal.getPipelineStageKey()), requested = open.indexOf(stageKey);
      if (current >= 0 && requested != current && requested != current + 1)
        throw new InvalidListQueryException("Move to the next ordered open stage");
    }
    deal.moveConfiguredPipeline(
        pipelineId,
        stageKey,
        next.outcome().equals("WON")
            ? OpportunityStage.CLOSED_WON
            : next.outcome().equals("LOST")
                ? OpportunityStage.CLOSED_LOST
                : OpportunityStage.QUALIFICATION);
    return OpportunityResponse.from(deals.save(deal));
  }
}
