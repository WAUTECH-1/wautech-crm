package com.wautech.crm.organization.controller;

import com.wautech.crm.organization.service.AssignmentService;
import com.wautech.crm.platform.tenant.AuthenticatedOrganizationContext;
import java.util.*;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api")
public class AssignmentController {
  private final AssignmentService service;
  private final AuthenticatedOrganizationContext context;

  public AssignmentController(AssignmentService s, AuthenticatedOrganizationContext c) {
    service = s;
    context = c;
  }

  public record AssignmentRequest(UUID userId) {}

  @GetMapping("/organization/eligible-members")
  public List<AssignmentService.EligibleMember> members() {
    return service.members(context.requireOrganizationId());
  }

  @PatchMapping("/tasks/{id}/assignee")
  public com.wautech.crm.task.dto.TaskResponse task(
      @PathVariable UUID id, @RequestBody AssignmentRequest request) {
    return service.task(context.requireOrganizationId(), id, request.userId());
  }

  @PatchMapping("/opportunities/{id}/owner")
  public com.wautech.crm.opportunity.dto.OpportunityResponse opportunity(
      @PathVariable UUID id, @RequestBody AssignmentRequest request) {
    return service.opportunity(context.requireOrganizationId(), id, request.userId());
  }
}
