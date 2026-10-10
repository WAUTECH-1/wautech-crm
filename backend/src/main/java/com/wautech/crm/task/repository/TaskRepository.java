package com.wautech.crm.task.repository;

import com.wautech.crm.task.entity.Task;
import com.wautech.crm.task.entity.TaskPriority;
import com.wautech.crm.task.entity.TaskStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface TaskRepository extends JpaRepository<Task, UUID> {
    @Query("select t from Task t where t.organization.id = :organizationId and t.archived = false " +
            "and (:companyId is null or t.company.id = :companyId) " +
            "and (:contactId is null or t.contact.id = :contactId) " +
            "and (:leadId is null or t.lead.id = :leadId) " +
            "and (:opportunityId is null or t.opportunity.id = :opportunityId) " +
            "and (:status is null or t.status = :status) " +
            "and (:priority is null or t.priority = :priority) " +
            "and (:search is null or lower(t.title) like :search escape '!' or lower(t.description) like :search escape '!') " +
            "and (:dueBefore is null or t.dueAt < :dueBefore) " +
            "and (:dueAfter is null or t.dueAt > :dueAfter) " +
            "and (:overdue is null or " +
            "(:overdue = true and t.dueAt is not null and t.dueAt < :now " +
            "and t.status in (com.wautech.crm.task.entity.TaskStatus.OPEN, " +
            "com.wautech.crm.task.entity.TaskStatus.IN_PROGRESS)) or " +
            "(:overdue = false and (t.dueAt is null or t.dueAt >= :now or " +
            "t.status not in (com.wautech.crm.task.entity.TaskStatus.OPEN, " +
            "com.wautech.crm.task.entity.TaskStatus.IN_PROGRESS)))) " +
            "order by case when t.dueAt is null then 1 else 0 end asc, t.dueAt asc, t.createdAt desc")
    List<Task> findActive(@Param("organizationId") UUID organizationId,
                          @Param("companyId") UUID companyId,
                          @Param("contactId") UUID contactId,
                          @Param("leadId") UUID leadId,
                          @Param("opportunityId") UUID opportunityId,
                          @Param("status") TaskStatus status,
                          @Param("priority") TaskPriority priority,
                          @Param("dueBefore") Instant dueBefore,
                          @Param("dueAfter") Instant dueAfter,
                          @Param("overdue") Boolean overdue,
                          @Param("search") String search,
                          @Param("now") Instant now);

    Optional<Task> findByIdAndOrganization_IdAndArchivedFalse(UUID id, UUID organizationId);
}
