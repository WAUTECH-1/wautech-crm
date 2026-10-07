package com.wautech.crm.task.service;

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

    public TaskService(TaskRepository taskRepository, CompanyRepository companyRepository,
                       ContactRepository contactRepository, LeadRepository leadRepository,
                       OpportunityRepository opportunityRepository) {
        this.taskRepository = taskRepository;
        this.companyRepository = companyRepository;
        this.contactRepository = contactRepository;
        this.leadRepository = leadRepository;
        this.opportunityRepository = opportunityRepository;
    }

    public TaskResponse create(TaskRequest request) {
        ParentRecords parents = findParents(request);
        Task task = new Task(parents.company(), parents.contact(), parents.lead(), parents.opportunity(),
                request.title().trim(), request.description(), request.priority(), request.dueAt());
        return TaskResponse.from(taskRepository.save(task));
    }

    @Transactional(readOnly = true)
    public List<TaskResponse> listActive(UUID companyId, UUID contactId, UUID leadId, UUID opportunityId,
                                         TaskStatus status, TaskPriority priority, Instant dueBefore,
                                         Instant dueAfter, Boolean overdue) {
        Instant now = Instant.now();
        return taskRepository.findActive(companyId, contactId, leadId, opportunityId, status, priority,
                        dueBefore, dueAfter, overdue, now)
                .stream().map(task -> TaskResponse.from(task, now)).toList();
    }

    @Transactional(readOnly = true)
    public List<TaskResponse> listActive(UUID companyId, UUID contactId, UUID leadId, UUID opportunityId,
                                         TaskStatus status, TaskPriority priority, Instant dueBefore,
                                         Instant dueAfter, Boolean overdue, String search,
                                         String sortBy, String sortDirection) {
        Instant now = Instant.now();
        List<Task> rows = new ArrayList<>(taskRepository.findActive(companyId, contactId, leadId, opportunityId, status, priority,
                        dueBefore, dueAfter, overdue, SearchText.containsPattern(search), now));
        ListSort.apply(rows, sortBy, sortDirection, SORT_FIELDS, Task::getId);
        return rows
                .stream().map(task -> TaskResponse.from(task, now)).toList();
    }

    @Transactional(readOnly = true)
    public TaskResponse getById(UUID id) {
        return TaskResponse.from(findActiveTask(id));
    }

    public TaskResponse update(UUID id, TaskRequest request) {
        Task task = findActiveTask(id);
        ParentRecords parents = findParents(request);
        task.update(parents.company(), parents.contact(), parents.lead(), parents.opportunity(),
                request.title().trim(), request.description(), request.priority(), request.dueAt());
        return TaskResponse.from(taskRepository.save(task));
    }

    public TaskResponse changeStatus(UUID id, TaskStatus status) {
        Task task = findActiveTask(id);
        task.changeStatus(status);
        return TaskResponse.from(taskRepository.save(task));
    }

    public void archive(UUID id) {
        Task task = findActiveTask(id);
        task.archive();
        taskRepository.save(task);
    }

    private ParentRecords findParents(TaskRequest request) {
        Company company = request.companyId() == null ? null : companyRepository
                .findByIdAndArchivedFalse(request.companyId())
                .orElseThrow(() -> new CompanyNotFoundException(request.companyId()));
        Contact contact = request.contactId() == null ? null : contactRepository
                .findByIdAndArchivedFalse(request.contactId())
                .orElseThrow(() -> new ContactNotFoundException(request.contactId()));
        Lead lead = request.leadId() == null ? null : leadRepository
                .findByIdAndArchivedFalse(request.leadId())
                .orElseThrow(() -> new LeadNotFoundException(request.leadId()));
        Opportunity opportunity = request.opportunityId() == null ? null : opportunityRepository
                .findByIdAndArchivedFalse(request.opportunityId())
                .orElseThrow(() -> new OpportunityNotFoundException(request.opportunityId()));
        return new ParentRecords(company, contact, lead, opportunity);
    }

    private Task findActiveTask(UUID id) {
        return taskRepository.findByIdAndArchivedFalse(id).orElseThrow(() -> new TaskNotFoundException(id));
    }

    private record ParentRecords(Company company, Contact contact, Lead lead, Opportunity opportunity) {
    }

    private static final Map<String, Function<Task, Comparable<?>>> SORT_FIELDS = Map.ofEntries(
            Map.entry("id", Task::getId), Map.entry("title", Task::getTitle),
            Map.entry("status", t -> t.getStatus().name()), Map.entry("priority", t -> t.getPriority().name()),
            Map.entry("dueAt", Task::getDueAt), Map.entry("createdAt", Task::getCreatedAt),
            Map.entry("updatedAt", Task::getUpdatedAt));
}
