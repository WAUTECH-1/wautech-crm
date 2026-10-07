package com.wautech.crm.task.service;

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
import com.wautech.crm.task.entity.IllegalTaskStatusTransitionException;
import com.wautech.crm.task.entity.Task;
import com.wautech.crm.task.entity.TaskPriority;
import com.wautech.crm.task.entity.TaskStatus;
import com.wautech.crm.task.repository.TaskRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class TaskServiceTest {
    @Mock private TaskRepository taskRepository;
    @Mock private CompanyRepository companyRepository;
    @Mock private ContactRepository contactRepository;
    @Mock private LeadRepository leadRepository;
    @Mock private OpportunityRepository opportunityRepository;
    @InjectMocks private TaskService taskService;

    @Test
    void createsTaskWithDefaultStatusAndPriority() {
        UUID companyId = UUID.randomUUID();
        Company company = company(companyId);
        when(companyRepository.findByIdAndArchivedFalse(companyId)).thenReturn(Optional.of(company));
        when(taskRepository.save(any(Task.class))).thenAnswer(invocation -> invocation.getArgument(0));

        TaskResponse response = taskService.create(request(companyId, null, null, null,
                "  Call prospect  ", null, null, null));

        assertEquals("Call prospect", response.title());
        assertEquals(TaskStatus.OPEN, response.status());
        assertEquals(TaskPriority.NORMAL, response.priority());
        assertNull(response.completedAt());
        assertFalse(response.archived());
    }

    @Test
    void acceptsEachParentTypeAndMultipleParents() {
        UUID companyId = UUID.randomUUID();
        UUID contactId = UUID.randomUUID();
        UUID leadId = UUID.randomUUID();
        UUID opportunityId = UUID.randomUUID();
        Company company = company(companyId);
        Contact contact = mock(Contact.class);
        Lead lead = mock(Lead.class);
        Opportunity opportunity = mock(Opportunity.class);
        when(companyRepository.findByIdAndArchivedFalse(companyId)).thenReturn(Optional.of(company));
        when(contactRepository.findByIdAndArchivedFalse(contactId)).thenReturn(Optional.of(contact));
        when(leadRepository.findByIdAndArchivedFalse(leadId)).thenReturn(Optional.of(lead));
        when(opportunityRepository.findByIdAndArchivedFalse(opportunityId)).thenReturn(Optional.of(opportunity));
        when(contact.getId()).thenReturn(contactId);
        when(lead.getId()).thenReturn(leadId);
        when(opportunity.getId()).thenReturn(opportunityId);
        when(taskRepository.save(any(Task.class))).thenAnswer(invocation -> invocation.getArgument(0));

        TaskResponse response = taskService.create(request(companyId, contactId, leadId, opportunityId,
                "Follow up", null, TaskPriority.HIGH, null));

        assertEquals(companyId, response.companyId());
        assertEquals(contactId, response.contactId());
        assertEquals(leadId, response.leadId());
        assertEquals(opportunityId, response.opportunityId());
        assertEquals(TaskPriority.HIGH, response.priority());
    }

    @Test
    void acceptsContactLeadAndOpportunityWithoutCompany() {
        UUID contactId = UUID.randomUUID();
        UUID leadId = UUID.randomUUID();
        UUID opportunityId = UUID.randomUUID();
        Contact contact = mock(Contact.class);
        Lead lead = mock(Lead.class);
        Opportunity opportunity = mock(Opportunity.class);
        when(contactRepository.findByIdAndArchivedFalse(contactId)).thenReturn(Optional.of(contact));
        when(leadRepository.findByIdAndArchivedFalse(leadId)).thenReturn(Optional.of(lead));
        when(opportunityRepository.findByIdAndArchivedFalse(opportunityId)).thenReturn(Optional.of(opportunity));
        when(contact.getId()).thenReturn(contactId);
        when(lead.getId()).thenReturn(leadId);
        when(opportunity.getId()).thenReturn(opportunityId);
        when(taskRepository.save(any(Task.class))).thenAnswer(invocation -> invocation.getArgument(0));

        assertEquals(contactId, taskService.create(request(null, contactId, null, null, "Call", null, null, null)).contactId());
        assertEquals(leadId, taskService.create(request(null, null, leadId, null, "Call", null, null, null)).leadId());
        assertEquals(opportunityId, taskService.create(request(null, null, null, opportunityId, "Call", null, null, null)).opportunityId());
    }

    @Test
    void createRejectsMissingOrArchivedParentOfEveryType() {
        UUID companyId = UUID.randomUUID();
        UUID contactId = UUID.randomUUID();
        UUID leadId = UUID.randomUUID();
        UUID opportunityId = UUID.randomUUID();
        when(companyRepository.findByIdAndArchivedFalse(companyId)).thenReturn(Optional.empty());
        when(contactRepository.findByIdAndArchivedFalse(contactId)).thenReturn(Optional.empty());
        when(leadRepository.findByIdAndArchivedFalse(leadId)).thenReturn(Optional.empty());
        when(opportunityRepository.findByIdAndArchivedFalse(opportunityId)).thenReturn(Optional.empty());

        assertThrows(CompanyNotFoundException.class,
                () -> taskService.create(request(companyId, null, null, null, "Task", null, null, null)));
        assertThrows(ContactNotFoundException.class,
                () -> taskService.create(request(null, contactId, null, null, "Task", null, null, null)));
        assertThrows(LeadNotFoundException.class,
                () -> taskService.create(request(null, null, leadId, null, "Task", null, null, null)));
        assertThrows(OpportunityNotFoundException.class,
                () -> taskService.create(request(null, null, null, opportunityId, "Task", null, null, null)));
        verify(taskRepository, never()).save(any());
    }

    @Test
    void getsAndUpdatesActiveTask() {
        UUID id = UUID.randomUUID();
        UUID companyId = UUID.randomUUID();
        Company company = company(companyId);
        Task task = new Task(company, null, null, null, "Old title", null, TaskPriority.NORMAL, null);
        when(taskRepository.findByIdAndArchivedFalse(id)).thenReturn(Optional.of(task));
        when(companyRepository.findByIdAndArchivedFalse(companyId)).thenReturn(Optional.of(company));
        when(taskRepository.save(task)).thenReturn(task);

        assertEquals("Old title", taskService.getById(id).title());
        TaskResponse updated = taskService.update(id,
                request(companyId, null, null, null, "New title", "Details", TaskPriority.URGENT,
                        Instant.parse("2026-11-01T10:00:00Z")));

        assertEquals("New title", updated.title());
        assertEquals("Details", updated.description());
        assertEquals(TaskPriority.URGENT, updated.priority());
        assertEquals(TaskStatus.OPEN, updated.status());
        assertNotNull(updated.updatedAt());
    }

    @Test
    void listUsesEveryFilterTogetherAndPassesOneClockInstantForOverdueCalculation() {
        UUID companyId = UUID.randomUUID();
        UUID contactId = UUID.randomUUID();
        UUID leadId = UUID.randomUUID();
        UUID opportunityId = UUID.randomUUID();
        Instant dueBefore = Instant.parse("2026-11-01T00:00:00Z");
        Instant dueAfter = Instant.parse("2026-10-01T00:00:00Z");
        when(taskRepository.findActive(eq(companyId), eq(contactId), eq(leadId), eq(opportunityId),
                eq(TaskStatus.OPEN), eq(TaskPriority.HIGH), eq(dueBefore), eq(dueAfter), eq(true), any(Instant.class)))
                .thenReturn(List.of());

        assertTrue(taskService.listActive(companyId, contactId, leadId, opportunityId, TaskStatus.OPEN,
                TaskPriority.HIGH, dueBefore, dueAfter, true).isEmpty());
        verify(taskRepository).findActive(eq(companyId), eq(contactId), eq(leadId), eq(opportunityId),
                eq(TaskStatus.OPEN), eq(TaskPriority.HIGH), eq(dueBefore), eq(dueAfter), eq(true), any(Instant.class));
    }

    @ParameterizedTest
    @CsvSource({
            "OPEN,IN_PROGRESS", "OPEN,COMPLETED", "OPEN,CANCELLED",
                "IN_PROGRESS,OPEN", "IN_PROGRESS,COMPLETED", "IN_PROGRESS,CANCELLED",
            "COMPLETED,OPEN", "CANCELLED,OPEN"
    })
    void permitsSpecifiedStatusTransitions(TaskStatus initial, TaskStatus next) {
        Task task = taskWithStatus(initial);
        task.changeStatus(next);
        assertEquals(next, task.getStatus());
    }

    @ParameterizedTest
    @MethodSource("invalidTransitions")
    void rejectsInvalidStatusTransitions(TaskStatus initial, TaskStatus next) {
        Task task = taskWithStatus(initial);
        assertThrows(IllegalTaskStatusTransitionException.class, () -> task.changeStatus(next));
        assertEquals(initial, task.getStatus());
    }

    static Stream<org.junit.jupiter.params.provider.Arguments> invalidTransitions() {
        return Stream.of(
                org.junit.jupiter.params.provider.Arguments.of(TaskStatus.OPEN, TaskStatus.OPEN),
                org.junit.jupiter.params.provider.Arguments.of(TaskStatus.IN_PROGRESS, TaskStatus.IN_PROGRESS),
                org.junit.jupiter.params.provider.Arguments.of(TaskStatus.COMPLETED, TaskStatus.IN_PROGRESS),
                org.junit.jupiter.params.provider.Arguments.of(TaskStatus.COMPLETED, TaskStatus.COMPLETED),
                org.junit.jupiter.params.provider.Arguments.of(TaskStatus.COMPLETED, TaskStatus.CANCELLED),
                org.junit.jupiter.params.provider.Arguments.of(TaskStatus.CANCELLED, TaskStatus.IN_PROGRESS),
                org.junit.jupiter.params.provider.Arguments.of(TaskStatus.CANCELLED, TaskStatus.COMPLETED),
                org.junit.jupiter.params.provider.Arguments.of(TaskStatus.CANCELLED, TaskStatus.CANCELLED)
        );
    }

    @Test
    void completionSetsServerTimestampAndReopeningOrCancellationClearsIt() {
        Task task = taskWithStatus(TaskStatus.OPEN);
        task.changeStatus(TaskStatus.COMPLETED);
        assertNotNull(task.getCompletedAt());

        task.changeStatus(TaskStatus.OPEN);
        assertNull(task.getCompletedAt());
        task.changeStatus(TaskStatus.CANCELLED);
        assertNull(task.getCompletedAt());
    }

    @Test
    void updateDoesNotChangeStatusOrCompletedAt() {
        UUID id = UUID.randomUUID();
        UUID companyId = UUID.randomUUID();
        Company company = company(companyId);
        Task task = taskWithStatus(TaskStatus.COMPLETED);
        Instant completedAt = task.getCompletedAt();
        when(taskRepository.findByIdAndArchivedFalse(id)).thenReturn(Optional.of(task));
        when(companyRepository.findByIdAndArchivedFalse(companyId)).thenReturn(Optional.of(company));
        when(taskRepository.save(task)).thenReturn(task);

        taskService.update(id, request(companyId, null, null, null, "Updated", null, TaskPriority.HIGH, null));

        assertEquals(TaskStatus.COMPLETED, task.getStatus());
        assertEquals(completedAt, task.getCompletedAt());
    }

    @Test
    void overdueUsesStrictDueBoundaryAndOnlyOpenOrInProgressTasks() {
        Instant dueAt = Instant.parse("2026-10-01T10:00:00Z");
        Task open = taskWithDueAt(dueAt);
        assertFalse(open.isOverdueAt(dueAt));
        assertTrue(open.isOverdueAt(dueAt.plusNanos(1)));
        assertFalse(open.isOverdueAt(dueAt.minusNanos(1)));
        assertTrue(TaskResponse.from(open, dueAt.plusNanos(1)).overdue());

        open.changeStatus(TaskStatus.COMPLETED);
        assertFalse(open.isOverdueAt(dueAt.plusSeconds(1)));
        assertFalse(TaskResponse.from(open, dueAt.plusSeconds(1)).overdue());
        Task cancelled = taskWithDueAt(dueAt);
        cancelled.changeStatus(TaskStatus.CANCELLED);
        assertFalse(cancelled.isOverdueAt(dueAt.plusSeconds(1)));
        assertFalse(taskWithDueAt(null).isOverdueAt(dueAt.plusSeconds(1)));
    }

    @Test
    void archivedTaskCannotBeReadUpdatedChangedOrArchivedAgain() {
        UUID id = UUID.randomUUID();
        when(taskRepository.findByIdAndArchivedFalse(id)).thenReturn(Optional.empty());
        TaskRequest request = request(UUID.randomUUID(), null, null, null, "Task", null, null, null);

        assertThrows(TaskNotFoundException.class, () -> taskService.getById(id));
        assertThrows(TaskNotFoundException.class, () -> taskService.update(id, request));
        assertThrows(TaskNotFoundException.class, () -> taskService.changeStatus(id, TaskStatus.COMPLETED));
        assertThrows(TaskNotFoundException.class, () -> taskService.archive(id));
        verifyNoInteractions(companyRepository, contactRepository, leadRepository, opportunityRepository);
    }

    @Test
    void archiveSoftDeletesTask() {
        UUID id = UUID.randomUUID();
        Task task = new Task(company(UUID.randomUUID()), null, null, null, "Task", null, null, null);
        when(taskRepository.findByIdAndArchivedFalse(id)).thenReturn(Optional.of(task));
        when(taskRepository.save(task)).thenReturn(task);

        taskService.archive(id);

        assertTrue(task.isArchived());
        assertNotNull(task.getUpdatedAt());
        verify(taskRepository, never()).delete(any());
    }

    private Task taskWithStatus(TaskStatus status) {
        Task task = taskWithDueAt(null);
        if (status == TaskStatus.IN_PROGRESS) task.changeStatus(TaskStatus.IN_PROGRESS);
        if (status == TaskStatus.COMPLETED) task.changeStatus(TaskStatus.COMPLETED);
        if (status == TaskStatus.CANCELLED) task.changeStatus(TaskStatus.CANCELLED);
        return task;
    }

    private Task taskWithDueAt(Instant dueAt) {
        return new Task(company(UUID.randomUUID()), null, null, null, "Task", null, TaskPriority.NORMAL, dueAt);
    }

    private Company company(UUID id) {
        Company company = mock(Company.class);
        when(company.getId()).thenReturn(id);
        return company;
    }

    private TaskRequest request(UUID companyId, UUID contactId, UUID leadId, UUID opportunityId, String title,
                                String description, TaskPriority priority, Instant dueAt) {
        return new TaskRequest(companyId, contactId, leadId, opportunityId, title, description, priority, dueAt);
    }
}
