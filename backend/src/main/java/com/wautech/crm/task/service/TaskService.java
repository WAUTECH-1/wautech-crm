package com.wautech.crm.task.service;

import com.wautech.crm.audit.AuditedMutation;
import com.wautech.crm.activity.service.ActivityNotFoundException;
import com.wautech.crm.company.entity.Company;
import com.wautech.crm.company.repository.CompanyRepository;
import com.wautech.crm.company.service.CompanyNotFoundException;
import com.wautech.crm.contact.entity.Contact;
import com.wautech.crm.contact.repository.ContactRepository;
import com.wautech.crm.contact.service.ContactNotFoundException;
import com.wautech.crm.lead.entity.Lead;
import com.wautech.crm.lead.repository.LeadRepository;
import com.wautech.crm.lead.service.LeadNotFoundException;
import com.wautech.crm.opportunity.entity.Opportunity;
import com.wautech.crm.opportunity.repository.OpportunityRepository;
import com.wautech.crm.opportunity.service.OpportunityNotFoundException;
import com.wautech.crm.task.dto.TaskRequest;
import com.wautech.crm.task.dto.TaskResponse;
import com.wautech.crm.task.entity.Task;
import com.wautech.crm.task.entity.TaskPriority;
import com.wautech.crm.task.entity.TaskStatus;
import com.wautech.crm.task.repository.TaskRepository;
import com.wautech.crm.platform.search.ListSort;
import com.wautech.crm.platform.search.SearchText;
import com.wautech.crm.organization.entity.Organization;
import com.wautech.crm.organization.service.OrganizationService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.ArrayList;
import java.util.UUID;
import java.util.Map;
import java.util.function.Function;

@Service
@Transactional
public class TaskService {
    private final TaskRepository taskRepository;
    private final CompanyRepository companyRepository;
    private final ContactRepository contactRepository;
    private final LeadRepository leadRepository;
    private final OpportunityRepository opportunityRepository;
    private final OrganizationService organizationService;

    public TaskService(TaskRepository taskRepository, CompanyRepository companyRepository,
                       ContactRepository contactRepository, LeadRepository leadRepository,
                       OpportunityRepository opportunityRepository, OrganizationService organizationService) {
        this.taskRepository = taskRepository;
        this.companyRepository = companyRepository;
        this.contactRepository = contactRepository;
        this.leadRepository = leadRepository;
        this.opportunityRepository = opportunityRepository;
        this.organizationService = organizationService;
    }

    @PreAuthorize("@crmAuthorization.canWrite(#p0)")
    @AuditedMutation(eventType = "TASK_CREATED", targetType = "TASK")
    public TaskResponse create(UUID organizationId, TaskRequest request) {
        Organization organization = organizationService.requireActiveOrganization(organizationId);
        ParentRecords parents = findParents(organizationId, request);
        Task task = new Task(organization, parents.company(), parents.contact(), parents.lead(), parents.opportunity(),
                request.title().trim(), request.description(), request.priority(), request.dueAt());
        return TaskResponse.from(taskRepository.save(task));
    }

    @Transactional(readOnly = true)
    @PreAuthorize("@crmAuthorization.canView(#p0)")
    public List<TaskResponse> listActive(UUID organizationId, UUID companyId, UUID contactId, UUID leadId, UUID opportunityId,
                                         TaskStatus status, TaskPriority priority, Instant dueBefore,
                                         Instant dueAfter, Boolean overdue) {
        organizationService.requireActiveOrganization(organizationId);
        Instant now = Instant.now();
        return taskRepository.findActive(organizationId, companyId, contactId, leadId, opportunityId, status, priority,
                        dueBefore, dueAfter, overdue, null, now)
                .stream().map(task -> TaskResponse.from(task, now)).toList();
    }

    @Transactional(readOnly = true)
    @PreAuthorize("@crmAuthorization.canView(#p0)")
    public List<TaskResponse> listActive(UUID organizationId, UUID companyId, UUID contactId, UUID leadId, UUID opportunityId,
                                         TaskStatus status, TaskPriority priority, Instant dueBefore,
                                         Instant dueAfter, Boolean overdue, String search,
                                         String sortBy, String sortDirection) {
        organizationService.requireActiveOrganization(organizationId);
        Instant now = Instant.now();
        List<Task> rows = new ArrayList<>(taskRepository.findActive(organizationId, companyId, contactId, leadId, opportunityId, status, priority,
                        dueBefore, dueAfter, overdue, SearchText.containsPattern(search), now));
        ListSort.apply(rows, sortBy, sortDirection, SORT_FIELDS, Task::getId);
        return rows
                .stream().map(task -> TaskResponse.from(task, now)).toList();
    }

    @Transactional(readOnly = true)
    @PreAuthorize("@crmAuthorization.canView(#p0)")
    public TaskResponse getById(UUID organizationId, UUID id) {
        organizationService.requireActiveOrganization(organizationId);
        return TaskResponse.from(findActiveTask(organizationId, id));
    }

    @PreAuthorize("@crmAuthorization.canWrite(#p0)")
    @AuditedMutation(eventType = "TASK_UPDATED", targetType = "TASK")
    public TaskResponse update(UUID organizationId, UUID id, TaskRequest request) {
        organizationService.requireActiveOrganization(organizationId);
        Task task = findActiveTask(organizationId, id);
        ParentRecords parents = findParents(organizationId, request);
        task.update(parents.company(), parents.contact(), parents.lead(), parents.opportunity(),
                request.title().trim(), request.description(), request.priority(), request.dueAt());
        return TaskResponse.from(taskRepository.save(task));
    }

    @PreAuthorize("@crmAuthorization.canWrite(#p0)")
    @AuditedMutation(eventType = "TASK_STATUS_CHANGED", targetType = "TASK")
    public TaskResponse changeStatus(UUID organizationId, UUID id, TaskStatus status) {
        organizationService.requireActiveOrganization(organizationId);
        Task task = findActiveTask(organizationId, id);
        task.changeStatus(status);
        return TaskResponse.from(taskRepository.save(task));
    }

    @PreAuthorize("@crmAuthorization.canWrite(#p0)")
    @AuditedMutation(eventType = "TASK_ARCHIVED", targetType = "TASK")
    public void archive(UUID organizationId, UUID id) {
        organizationService.requireActiveOrganization(organizationId);
        Task task = findActiveTask(organizationId, id);
        task.archive();
        taskRepository.save(task);
    }

    private ParentRecords findParents(UUID organizationId, TaskRequest request) {
        Company company = request.companyId() == null ? null : companyRepository
                .findByIdAndOrganization_IdAndArchivedFalse(request.companyId(), organizationId)
                .orElseThrow(() -> new CompanyNotFoundException(request.companyId()));
        Contact contact = request.contactId() == null ? null : contactRepository
                .findByIdAndOrganization_IdAndArchivedFalse(request.contactId(), organizationId)
                .orElseThrow(() -> new ContactNotFoundException(request.contactId()));
        Lead lead = request.leadId() == null ? null : leadRepository
                .findByIdAndOrganization_IdAndArchivedFalse(request.leadId(), organizationId)
                .orElseThrow(() -> new LeadNotFoundException(request.leadId()));
        Opportunity opportunity = request.opportunityId() == null ? null : opportunityRepository
                .findByIdAndOrganization_IdAndArchivedFalse(request.opportunityId(), organizationId)
                .orElseThrow(() -> new OpportunityNotFoundException(request.opportunityId()));
        return new ParentRecords(company, contact, lead, opportunity);
    }

    private Task findActiveTask(UUID organizationId, UUID id) {
        return taskRepository.findByIdAndOrganization_IdAndArchivedFalse(id, organizationId)
                .orElseThrow(() -> new TaskNotFoundException(id));
    }

    private record ParentRecords(Company company, Contact contact, Lead lead, Opportunity opportunity) {
    }

    private static final Map<String, Function<Task, Comparable<?>>> SORT_FIELDS = Map.ofEntries(
            Map.entry("id", Task::getId), Map.entry("title", Task::getTitle),
            Map.entry("status", t -> t.getStatus().name()), Map.entry("priority", t -> t.getPriority().name()),
            Map.entry("dueAt", Task::getDueAt), Map.entry("createdAt", Task::getCreatedAt),
            Map.entry("updatedAt", Task::getUpdatedAt));
}
