package com.wautech.crm.task.service;

import com.wautech.crm.notification.entity.*;
import com.wautech.crm.notification.service.NotificationService;
import com.wautech.crm.organization.entity.MembershipStatus;
import com.wautech.crm.organization.repository.OrganizationMembershipRepository;
import com.wautech.crm.organization.service.OrganizationService;
import com.wautech.crm.platform.security.CrmAuthorization;
import com.wautech.crm.task.entity.Task;
import com.wautech.crm.task.entity.TaskStatus;
import jakarta.persistence.*;
import java.time.Instant;
import java.util.*;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class TaskReminderService {
  private final EntityManager em;
  private final OrganizationService organizations;
  private final OrganizationMembershipRepository memberships;
  private final NotificationService notifications;
  private final CrmAuthorization authorization;

  public TaskReminderService(
      EntityManager e,
      OrganizationService o,
      OrganizationMembershipRepository m,
      NotificationService n,
      CrmAuthorization a) {
    em = e;
    organizations = o;
    memberships = m;
    notifications = n;
    authorization = a;
  }

  @PreAuthorize("@crmAuthorization.canView(#p0)")
  public int checkMine(UUID org) {
    return send(org, authorization.currentUserId());
  }

  /**
   * Internal scheduler entry. All recipients are revalidated and each tenant is locked
   * independently.
   */
  public int checkScheduled() {
    return send(null, null);
  }

  private int send(UUID org, UUID user) {
    String predicate = org == null ? "" : " and t.organization.id=:org and t.assigneeUserId=:user";
    var q =
        em.createQuery(
                "select t from Task t where t.archived=false and t.organization.archived=false and"
                    + " t.assigneeUserId is not null and t.dueAt<=:now and t.status in"
                    + " (:open,:progress) and (t.reminderDueAt is null or t.reminderDueAt<>t.dueAt"
                    + " or t.reminderUserId<>t.assigneeUserId)"
                    + predicate
                    + " order by t.dueAt,t.id",
                Task.class)
            .setParameter("now", Instant.now())
            .setParameter("open", TaskStatus.OPEN)
            .setParameter("progress", TaskStatus.IN_PROGRESS)
            .setMaxResults(100);
    if (org != null) q.setParameter("org", org).setParameter("user", user);
    int count = 0;
    for (var task : q.getResultList()) {
      UUID tenant = task.getOrganization().getId();
      organizations.lockActiveOrganization(tenant);
      em.refresh(task, LockModeType.PESSIMISTIC_WRITE);
      UUID recipient = task.getAssigneeUserId();
      if (recipient == null
          || task.isArchived()
          || !task.isOverdueAt(Instant.now())
          || memberships
              .findByOrganization_IdAndUser_IdAndStatus(tenant, recipient, MembershipStatus.ACTIVE)
              .filter(m -> m.getUser().isEnabled())
              .isEmpty()) continue;
      notifications.createFromTrustedEvent(
          tenant,
          recipient,
          NotificationType.TASK_DUE,
          "Task is due",
          task.getTitle(),
          NotificationTargetType.TASK,
          task.getId(),
          "task-due:" + task.getId() + ":" + recipient + ":" + task.getDueAt());
      task.markReminded();
      count++;
    }
    return count;
  }
}
