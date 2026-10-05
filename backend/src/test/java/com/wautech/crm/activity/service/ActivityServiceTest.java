package com.wautech.crm.activity.service;

import com.wautech.crm.activity.dto.ActivityRequest;
import com.wautech.crm.activity.dto.ActivityResponse;
import com.wautech.crm.activity.entity.Activity;
import com.wautech.crm.activity.entity.ActivityType;
import com.wautech.crm.activity.repository.ActivityRepository;
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
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ActivityServiceTest {
    @Mock private ActivityRepository activityRepository;
    @Mock private CompanyRepository companyRepository;
    @Mock private ContactRepository contactRepository;
    @Mock private LeadRepository leadRepository;
    @Mock private OpportunityRepository opportunityRepository;
    @InjectMocks private ActivityService activityService;

    @ParameterizedTest
    @EnumSource(ActivityType.class)
    void createsEverySupportedActivityType(ActivityType type) {
        UUID companyId = UUID.randomUUID();
        Company company = company(companyId);
        when(companyRepository.findByIdAndArchivedFalse(companyId)).thenReturn(Optional.of(company));
        when(activityRepository.save(any(Activity.class))).thenAnswer(invocation -> invocation.getArgument(0));

        ActivityResponse response = activityService.create(request(companyId, null, null, null, type));

        assertEquals(type, response.type());
        assertEquals(companyId, response.companyId());
        assertEquals("Customer discussion", response.subject());
    }

    @Test
    void associatesActivityWithEachSupportedParentType() {
        UUID companyId = UUID.randomUUID();
        UUID contactId = UUID.randomUUID();
        UUID leadId = UUID.randomUUID();
        UUID opportunityId = UUID.randomUUID();
        Company company = company(companyId);
        Contact contact = mock(Contact.class);
        Lead lead = mock(Lead.class);
        Opportunity opportunity = mock(Opportunity.class);
        when(contactRepository.findByIdAndArchivedFalse(contactId)).thenReturn(Optional.of(contact));
        when(contact.getId()).thenReturn(contactId);
        when(leadRepository.findByIdAndArchivedFalse(leadId)).thenReturn(Optional.of(lead));
        when(lead.getId()).thenReturn(leadId);
        when(opportunityRepository.findByIdAndArchivedFalse(opportunityId)).thenReturn(Optional.of(opportunity));
        when(opportunity.getId()).thenReturn(opportunityId);
        when(companyRepository.findByIdAndArchivedFalse(companyId)).thenReturn(Optional.of(company));
        when(activityRepository.save(any(Activity.class))).thenAnswer(invocation -> invocation.getArgument(0));

        ActivityResponse response = activityService.create(
                request(companyId, contactId, leadId, opportunityId, ActivityType.MEETING));

        assertEquals(companyId, response.companyId());
        assertEquals(contactId, response.contactId());
        assertEquals(leadId, response.leadId());
        assertEquals(opportunityId, response.opportunityId());
        verify(companyRepository).findByIdAndArchivedFalse(companyId);
        verify(contactRepository).findByIdAndArchivedFalse(contactId);
        verify(leadRepository).findByIdAndArchivedFalse(leadId);
        verify(opportunityRepository).findByIdAndArchivedFalse(opportunityId);
    }

    @Test
    void contactLeadAndOpportunityCanEachBeUsedWithoutACompany() {
        UUID contactId = UUID.randomUUID();
        UUID leadId = UUID.randomUUID();
        UUID opportunityId = UUID.randomUUID();
        Contact contact = mock(Contact.class);
        Lead lead = mock(Lead.class);
        Opportunity opportunity = mock(Opportunity.class);
        when(contactRepository.findByIdAndArchivedFalse(contactId)).thenReturn(Optional.of(contact));
        when(contact.getId()).thenReturn(contactId);
        when(leadRepository.findByIdAndArchivedFalse(leadId)).thenReturn(Optional.of(lead));
        when(lead.getId()).thenReturn(leadId);
        when(opportunityRepository.findByIdAndArchivedFalse(opportunityId)).thenReturn(Optional.of(opportunity));
        when(opportunity.getId()).thenReturn(opportunityId);
        when(activityRepository.save(any(Activity.class))).thenAnswer(invocation -> invocation.getArgument(0));

        ActivityResponse contactActivity = activityService.create(
                request(null, contactId, null, null, ActivityType.CALL));
        ActivityResponse leadActivity = activityService.create(
                request(null, null, leadId, null, ActivityType.EMAIL));
        ActivityResponse opportunityActivity = activityService.create(
                request(null, null, null, opportunityId, ActivityType.NOTE));

        assertNull(contactActivity.companyId());
        assertEquals(contactId, contactActivity.contactId());
        assertNull(leadActivity.companyId());
        assertEquals(leadId, leadActivity.leadId());
        assertNull(opportunityActivity.companyId());
        assertEquals(opportunityId, opportunityActivity.opportunityId());
    }

    @Test
    void rejectsMissingOrArchivedCompanyParent() {
        UUID companyId = UUID.randomUUID();
        when(companyRepository.findByIdAndArchivedFalse(companyId)).thenReturn(Optional.empty());

        assertThrows(CompanyNotFoundException.class,
                () -> activityService.create(request(companyId, null, null, null, ActivityType.CALL)));
        verify(activityRepository, never()).save(any());
    }

    @Test
    void rejectsMissingOrArchivedContactParent() {
        UUID contactId = UUID.randomUUID();
        when(contactRepository.findByIdAndArchivedFalse(contactId)).thenReturn(Optional.empty());

        assertThrows(ContactNotFoundException.class,
                () -> activityService.create(request(null, contactId, null, null, ActivityType.CALL)));
        verify(activityRepository, never()).save(any());
    }

    @Test
    void rejectsMissingOrArchivedLeadParent() {
        UUID leadId = UUID.randomUUID();
        when(leadRepository.findByIdAndArchivedFalse(leadId)).thenReturn(Optional.empty());

        assertThrows(LeadNotFoundException.class,
                () -> activityService.create(request(null, null, leadId, null, ActivityType.CALL)));
        verify(activityRepository, never()).save(any());
    }

    @Test
    void rejectsMissingOrArchivedOpportunityParent() {
        UUID opportunityId = UUID.randomUUID();
        when(opportunityRepository.findByIdAndArchivedFalse(opportunityId)).thenReturn(Optional.empty());

        assertThrows(OpportunityNotFoundException.class,
                () -> activityService.create(request(null, null, null, opportunityId, ActivityType.CALL)));
        verify(activityRepository, never()).save(any());
    }

    @Test
    void getsAndUpdatesActiveActivity() {
        UUID id = UUID.randomUUID();
        UUID companyId = UUID.randomUUID();
        Company company = company(companyId);
        Activity activity = activity(company, "Before update", ActivityType.CALL, instant("2026-01-01T10:00:00Z"));
        when(activityRepository.findByIdAndArchivedFalse(id)).thenReturn(Optional.of(activity));
        when(companyRepository.findByIdAndArchivedFalse(companyId)).thenReturn(Optional.of(company));
        when(activityRepository.save(activity)).thenReturn(activity);

        assertEquals("Before update", activityService.getById(id).subject());
        ActivityResponse updated = activityService.update(id,
                request(companyId, null, null, null, ActivityType.EMAIL, "Updated subject"));

        assertEquals("Updated subject", updated.subject());
        assertEquals(ActivityType.EMAIL, updated.type());
        assertNotNull(updated.updatedAt());
    }

    @Test
    void archivedActivityCannotBeRetrievedUpdatedOrArchivedAgain() {
        UUID id = UUID.randomUUID();
        when(activityRepository.findByIdAndArchivedFalse(id)).thenReturn(Optional.empty());
        ActivityRequest request = request(UUID.randomUUID(), null, null, null, ActivityType.NOTE);

        assertThrows(ActivityNotFoundException.class, () -> activityService.getById(id));
        assertThrows(ActivityNotFoundException.class, () -> activityService.update(id, request));
        assertThrows(ActivityNotFoundException.class, () -> activityService.archive(id));
        verifyNoInteractions(companyRepository, contactRepository, leadRepository, opportunityRepository);
    }

    @Test
    void listUsesAllFiltersAndPreservesOccurredAtDescendingRepositoryOrder() {
        UUID companyId = UUID.randomUUID();
        UUID contactId = UUID.randomUUID();
        UUID leadId = UUID.randomUUID();
        UUID opportunityId = UUID.randomUUID();
        Activity newer = activity(null, "Newer", ActivityType.CALL, instant("2026-02-01T10:00:00Z"));
        Activity older = activity(null, "Older", ActivityType.CALL, instant("2026-01-01T10:00:00Z"));
        when(activityRepository.findActive(companyId, contactId, leadId, opportunityId, ActivityType.CALL))
                .thenReturn(List.of(newer, older));

        List<ActivityResponse> response = activityService.listActive(companyId, contactId, leadId,
                opportunityId, ActivityType.CALL);

        assertEquals(List.of("Newer", "Older"), response.stream().map(ActivityResponse::subject).toList());
        verify(activityRepository).findActive(companyId, contactId, leadId, opportunityId, ActivityType.CALL);
    }

    @Test
    void archiveSoftDeletesActivity() {
        UUID id = UUID.randomUUID();
        Activity activity = activity(null, "Call", ActivityType.CALL, instant("2026-01-01T10:00:00Z"));
        when(activityRepository.findByIdAndArchivedFalse(id)).thenReturn(Optional.of(activity));
        when(activityRepository.save(activity)).thenReturn(activity);

        activityService.archive(id);

        assertTrue(activity.isArchived());
        assertNotNull(activity.getUpdatedAt());
        verify(activityRepository, never()).delete(any());
    }

    private Company company(UUID id) {
        Company company = mock(Company.class);
        when(company.getId()).thenReturn(id);
        return company;
    }

    private Activity activity(Company company, String subject, ActivityType type, Instant occurredAt) {
        return new Activity(company, null, null, null, type, subject, "Details", occurredAt);
    }

    private ActivityRequest request(UUID companyId, UUID contactId, UUID leadId, UUID opportunityId,
                                    ActivityType type) {
        return request(companyId, contactId, leadId, opportunityId, type, "Customer discussion");
    }

    private ActivityRequest request(UUID companyId, UUID contactId, UUID leadId, UUID opportunityId,
                                    ActivityType type, String subject) {
        return new ActivityRequest(companyId, contactId, leadId, opportunityId, type, subject,
                "Discussed next steps", instant("2026-01-01T10:00:00Z"));
    }

    private Instant instant(String value) {
        return Instant.parse(value);
    }
}
