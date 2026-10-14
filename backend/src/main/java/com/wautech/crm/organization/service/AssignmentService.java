package com.wautech.crm.organization.service;

import com.wautech.crm.audit.AuditedMutation;
import com.wautech.crm.opportunity.dto.OpportunityResponse;
import com.wautech.crm.opportunity.repository.OpportunityRepository;
import com.wautech.crm.opportunity.service.OpportunityNotFoundException;
import com.wautech.crm.organization.entity.MembershipStatus;
import com.wautech.crm.organization.repository.OrganizationMembershipRepository;
import com.wautech.crm.platform.search.InvalidListQueryException;
import com.wautech.crm.task.dto.TaskResponse;
import com.wautech.crm.task.repository.TaskRepository;
import com.wautech.crm.task.service.TaskNotFoundException;
import java.util.*;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class AssignmentService {
  private final OrganizationService organizations;
  private final OrganizationMembershipRepository memberships;
  private final com.wautech.crm.notification.service.NotificationService notifications;
  private final TaskRepository tasks;
  private final OpportunityRepository opportunities;

  public AssignmentService(
      OrganizationService o,
      OrganizationMembershipRepository m,
      TaskRepository t,
      OpportunityRepository p,
      com.wautech.crm.notification.service.NotificationService n) {
    organizations = o;
    memberships = m;
    tasks = t;
    opportunities = p;
    notifications = n;
  }

  public record EligibleMember(UUID userId, String name) {}

  @Transactional(readOnly = true)
  @PreAuthorize("@crmAuthorization.canView(#p0)")
  public List<EligibleMember> members(UUID organizationId) {
    organizations.requireActiveOrganization(organizationId);
    return memberships.findAllByOrganization_IdOrderByCreatedAtDesc(organizationId).stream()
        .filter(m -> m.getStatus() == MembershipStatus.ACTIVE && m.getUser().isEnabled())
        .map(
            m ->
                new EligibleMember(
                    m.getUser().getId(),
                    m.getUser().getFirstName() + " " + m.getUser().getLastName()))
        .toList();
  }

  private void validate(UUID organizationId, UUID userId) {
    organizations.lockActiveOrganization(organizationId);
    if (userId != null
        && memberships
            .findByOrganization_IdAndUser_IdAndStatus(
                organizationId, userId, MembershipStatus.ACTIVE)
            .filter(m -> m.getUser().isEnabled())
            .isEmpty())
      throw new InvalidListQueryException("Choose an active member of this organization");
  }

  @PreAuthorize("@crmAuthorization.canWrite(#p0)")
  @AuditedMutation(eventType = "TASK_UPDATED", targetType = "TASK")
  public TaskResponse task(UUID organizationId, UUID id, UUID userId) {
    validate(organizationId, userId);
    var record =
        tasks
            .findByIdAndOrganization_IdAndArchivedFalse(id, organizationId)
            .orElseThrow(() -> new TaskNotFoundException(id));
    var previous = record.getAssigneeUserId();
    record.assign(userId);
    if (userId != null && !Objects.equals(previous, userId))
      notifications.createFromTrustedEvent(
          organizationId,
          userId,
          com.wautech.crm.notification.entity.NotificationType.TASK_ASSIGNED,
          "Task assigned",
          record.getTitle(),
          com.wautech.crm.notification.entity.NotificationTargetType.TASK,
          id,
          "task-assigned:" + id + ":" + userId + ":" + record.getUpdatedAt());
    return TaskResponse.from(tasks.save(record));
  }

  @PreAuthorize("@crmAuthorization.canWrite(#p0)")
  @AuditedMutation(eventType = "OPPORTUNITY_UPDATED", targetType = "OPPORTUNITY")
  public OpportunityResponse opportunity(UUID organizationId, UUID id, UUID userId) {
    validate(organizationId, userId);
    var record =
        opportunities
            .findByIdAndOrganization_IdAndArchivedFalse(id, organizationId)
            .orElseThrow(() -> new OpportunityNotFoundException(id));
    var previous = record.getOwnerUserId();
    record.assign(userId);
    if (userId != null && !Objects.equals(previous, userId))
      notifications.createFromTrustedEvent(
          organizationId,
          userId,
          com.wautech.crm.notification.entity.NotificationType.OPPORTUNITY_ASSIGNED,
          "Opportunity assigned",
          record.getName(),
          com.wautech.crm.notification.entity.NotificationTargetType.OPPORTUNITY,
          id,
          "deal-assigned:" + id + ":" + userId + ":" + record.getUpdatedAt());
    return OpportunityResponse.from(opportunities.save(record));
  }
}
