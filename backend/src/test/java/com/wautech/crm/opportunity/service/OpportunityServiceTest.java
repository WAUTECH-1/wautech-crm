package com.wautech.crm.opportunity.service;

import com.wautech.crm.TestOrganization;
import com.wautech.crm.organization.service.OrganizationService;
import com.wautech.crm.company.entity.Company;
import com.wautech.crm.company.repository.CompanyRepository;
import com.wautech.crm.company.service.CompanyNotFoundException;
import com.wautech.crm.contact.entity.Contact;
import com.wautech.crm.contact.repository.ContactRepository;
import com.wautech.crm.contact.service.ContactNotFoundException;
import com.wautech.crm.opportunity.dto.OpportunityRequest;
import com.wautech.crm.opportunity.dto.OpportunityResponse;
import com.wautech.crm.opportunity.entity.IllegalOpportunityStageTransitionException;
import com.wautech.crm.opportunity.entity.Opportunity;
import com.wautech.crm.opportunity.entity.OpportunityStage;
import com.wautech.crm.opportunity.repository.OpportunityRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class OpportunityServiceTest {
    @Mock private OpportunityRepository opportunityRepository;
    @Mock private CompanyRepository companyRepository;
    @Mock private ContactRepository contactRepository;
    @Mock private OrganizationService organizationService;
    @InjectMocks private OpportunityService opportunityService;

    @Test
    void createsOpportunityWithCompanyAndDefaultsStage() {
        UUID companyId = UUID.randomUUID();
        Company company = company(companyId);
        when(companyRepository.findByIdAndOrganization_IdAndArchivedFalse(companyId, TestOrganization.ID)).thenReturn(Optional.of(company));
        when(opportunityRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        OpportunityResponse result = opportunityService.create(TestOrganization.ID, request(companyId, null, null));

        assertEquals(companyId, result.companyId());
        assertNull(result.contactId());
        assertEquals(OpportunityStage.QUALIFICATION, result.stage());
        assertEquals("New deal", result.name());
    }

    @Test
    void createsOpportunityWithContactFromSameCompany() {
        UUID companyId = UUID.randomUUID();
        UUID contactId = UUID.randomUUID();
        Company company = company(companyId);
        Contact contact = contact(contactId, company);
        when(companyRepository.findByIdAndOrganization_IdAndArchivedFalse(companyId, TestOrganization.ID)).thenReturn(Optional.of(company));
        when(contactRepository.findByIdAndOrganization_IdAndArchivedFalse(contactId, TestOrganization.ID)).thenReturn(Optional.of(contact));
        when(opportunityRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        OpportunityResponse result = opportunityService.create(TestOrganization.ID, request(companyId, contactId, null));

        assertEquals(contactId, result.contactId());
    }

    @Test
    void rejectsMissingAndArchivedCompany() {
        UUID companyId = UUID.randomUUID();
        when(companyRepository.findByIdAndOrganization_IdAndArchivedFalse(companyId, TestOrganization.ID)).thenReturn(Optional.empty());

        assertThrows(CompanyNotFoundException.class, () -> opportunityService.create(TestOrganization.ID, request(companyId, null, null)));
        verify(opportunityRepository, never()).save(any());
    }

    @Test
    void rejectsMissingAndArchivedContact() {
        UUID companyId = UUID.randomUUID();
        UUID contactId = UUID.randomUUID();
        Company company = new Company(new com.wautech.crm.organization.entity.Organization("Test Organization"), "Example", null, null, null, null, "ACTIVE");
        when(companyRepository.findByIdAndOrganization_IdAndArchivedFalse(companyId, TestOrganization.ID)).thenReturn(Optional.of(company));
        when(contactRepository.findByIdAndOrganization_IdAndArchivedFalse(contactId, TestOrganization.ID)).thenReturn(Optional.empty());

        assertThrows(ContactNotFoundException.class, () -> opportunityService.create(TestOrganization.ID, request(companyId, contactId, null)));
        verify(opportunityRepository, never()).save(any());
    }

    @Test
    void rejectsContactFromDifferentCompany() {
        UUID companyId = UUID.randomUUID();
        UUID contactId = UUID.randomUUID();
        Company company = company(companyId);
        Company otherCompany = company(UUID.randomUUID());
        Contact contact = mock(Contact.class);
        when(contact.getCompany()).thenReturn(otherCompany);
        when(companyRepository.findByIdAndOrganization_IdAndArchivedFalse(companyId, TestOrganization.ID)).thenReturn(Optional.of(company));
        when(contactRepository.findByIdAndOrganization_IdAndArchivedFalse(contactId, TestOrganization.ID))
                .thenReturn(Optional.of(contact));

        assertThrows(OpportunityContactCompanyMismatchException.class,
                () -> opportunityService.create(TestOrganization.ID, request(companyId, contactId, null)));
        verify(opportunityRepository, never()).save(any());
    }

    @Test
    void retrievesAndUpdatesActiveOpportunityWithoutChangingStageThroughPut() {
        UUID id = UUID.randomUUID();
        Company company = company(UUID.randomUUID());
        Opportunity opportunity = new Opportunity(new com.wautech.crm.organization.entity.Organization("Test Organization"), "Old", null, null, null, OpportunityStage.PROPOSAL,
                null, company, null);
        when(opportunityRepository.findByIdAndOrganization_IdAndArchivedFalse(id, TestOrganization.ID)).thenReturn(Optional.of(opportunity));
        when(companyRepository.findByIdAndOrganization_IdAndArchivedFalse(company.getId(), TestOrganization.ID)).thenReturn(Optional.of(company));
        when(opportunityRepository.save(opportunity)).thenReturn(opportunity);

        assertEquals(OpportunityStage.PROPOSAL, opportunityService.getById(TestOrganization.ID, id).stage());
        OpportunityResponse result = opportunityService.update(TestOrganization.ID, id,
                new OpportunityRequest(" Updated ", null, null, null, OpportunityStage.CLOSED_WON,
                        LocalDate.of(2027, 1, 1), company.getId(), null));

        assertEquals("Updated", result.name());
        assertEquals(OpportunityStage.PROPOSAL, result.stage());
        assertEquals(LocalDate.of(2027, 1, 1), result.expectedCloseDate());
    }

    @Test
    void missingOrArchivedOpportunityIsNotFoundForReadUpdateStageAndArchive() {
        UUID id = UUID.randomUUID();
        when(opportunityRepository.findByIdAndOrganization_IdAndArchivedFalse(id, TestOrganization.ID)).thenReturn(Optional.empty());
        OpportunityRequest request = request(UUID.randomUUID(), null, null);

        assertThrows(OpportunityNotFoundException.class, () -> opportunityService.getById(TestOrganization.ID, id));
        assertThrows(OpportunityNotFoundException.class, () -> opportunityService.update(TestOrganization.ID, id, request));
        assertThrows(OpportunityNotFoundException.class,
                () -> opportunityService.changeStage(TestOrganization.ID, id, OpportunityStage.NEEDS_ANALYSIS));
        assertThrows(OpportunityNotFoundException.class, () -> opportunityService.archive(TestOrganization.ID, id));
        verifyNoInteractions(companyRepository, contactRepository);
    }

    @ParameterizedTest
    @EnumSource(value = OpportunityStage.class, names = {"NEEDS_ANALYSIS", "CLOSED_LOST"})
    void permitsTransitionsFromQualification(OpportunityStage next) {
        assertTransition(OpportunityStage.QUALIFICATION, next);
    }

    @ParameterizedTest
    @EnumSource(value = OpportunityStage.class, names = {"PROPOSAL", "CLOSED_LOST"})
    void permitsTransitionsFromNeedsAnalysis(OpportunityStage next) {
        assertTransition(OpportunityStage.NEEDS_ANALYSIS, next);
    }

    @ParameterizedTest
    @EnumSource(value = OpportunityStage.class, names = {"NEGOTIATION", "CLOSED_LOST"})
    void permitsTransitionsFromProposal(OpportunityStage next) {
        assertTransition(OpportunityStage.PROPOSAL, next);
    }

    @ParameterizedTest
    @EnumSource(value = OpportunityStage.class, names = {"CLOSED_WON", "CLOSED_LOST"})
    void permitsTransitionsFromNegotiation(OpportunityStage next) {
        assertTransition(OpportunityStage.NEGOTIATION, next);
    }

    @ParameterizedTest
    @EnumSource(value = OpportunityStage.class, names = {"QUALIFICATION", "PROPOSAL", "NEGOTIATION", "CLOSED_WON"})
    void rejectsInvalidTransitionsFromQualification(OpportunityStage next) {
        assertRejectedTransition(OpportunityStage.QUALIFICATION, next);
    }

    @ParameterizedTest
    @EnumSource(value = OpportunityStage.class, names = {"QUALIFICATION", "NEEDS_ANALYSIS", "NEGOTIATION", "CLOSED_WON"})
    void rejectsInvalidTransitionsFromNeedsAnalysis(OpportunityStage next) {
        assertRejectedTransition(OpportunityStage.NEEDS_ANALYSIS, next);
    }

    @ParameterizedTest
    @EnumSource(value = OpportunityStage.class, names = {"QUALIFICATION", "NEEDS_ANALYSIS", "PROPOSAL", "CLOSED_WON"})
    void rejectsInvalidTransitionsFromProposal(OpportunityStage next) {
        assertRejectedTransition(OpportunityStage.PROPOSAL, next);
    }

    @ParameterizedTest
    @EnumSource(value = OpportunityStage.class, names = {"QUALIFICATION", "NEEDS_ANALYSIS", "PROPOSAL"})
    void rejectsInvalidTransitionsFromNegotiation(OpportunityStage next) {
        assertRejectedTransition(OpportunityStage.NEGOTIATION, next);
    }

    @ParameterizedTest
    @EnumSource(OpportunityStage.class)
    void closedWonAndClosedLostAreTerminal(OpportunityStage next) {
        assertRejectedTransition(OpportunityStage.CLOSED_WON, next);
        assertRejectedTransition(OpportunityStage.CLOSED_LOST, next);
    }

    @Test
    void listUsesAllActiveFilters() {
        UUID companyId = UUID.randomUUID();
        UUID contactId = UUID.randomUUID();
        when(opportunityRepository.findActive(TestOrganization.ID, companyId, contactId, OpportunityStage.PROPOSAL, null)).thenReturn(List.of());

        assertTrue(opportunityService.listActive(TestOrganization.ID, companyId, contactId, OpportunityStage.PROPOSAL).isEmpty());

        verify(opportunityRepository).findActive(TestOrganization.ID, companyId, contactId, OpportunityStage.PROPOSAL, null);
    }

    @Test
    void archiveSoftDeletesOpportunity() {
        UUID id = UUID.randomUUID();
        Opportunity opportunity = new Opportunity(new com.wautech.crm.organization.entity.Organization("Test Organization"), "Deal", null, null, null, null, null, null, null);
        when(opportunityRepository.findByIdAndOrganization_IdAndArchivedFalse(id, TestOrganization.ID)).thenReturn(Optional.of(opportunity));
        when(opportunityRepository.save(opportunity)).thenReturn(opportunity);

        opportunityService.archive(TestOrganization.ID, id);

        assertTrue(opportunity.isArchived());
        assertNotNull(opportunity.getUpdatedAt());
    }

    private void assertTransition(OpportunityStage current, OpportunityStage next) {
        UUID id = UUID.randomUUID();
        Opportunity opportunity = opportunityAt(current, company(UUID.randomUUID()));
        when(opportunityRepository.findByIdAndOrganization_IdAndArchivedFalse(id, TestOrganization.ID)).thenReturn(Optional.of(opportunity));
        when(opportunityRepository.save(opportunity)).thenReturn(opportunity);
        assertEquals(next, opportunityService.changeStage(TestOrganization.ID, id, next).stage());
    }

    private void assertRejectedTransition(OpportunityStage current, OpportunityStage next) {
        Opportunity opportunity = opportunityAt(current, null);
        assertThrows(IllegalOpportunityStageTransitionException.class, () -> opportunity.changeStage(next));
    }

    private Opportunity opportunityAt(OpportunityStage stage, Company company) {
        return new Opportunity(new com.wautech.crm.organization.entity.Organization("Test Organization"), "Deal", null, null, null, stage, null, company, null);
    }

    private Company company(UUID id) {
        Company company = mock(Company.class);
        when(company.getId()).thenReturn(id);
        return company;
    }

    private Contact contact(UUID id, Company company) {
        Contact contact = mock(Contact.class);
        when(contact.getId()).thenReturn(id);
        when(contact.getCompany()).thenReturn(company);
        return contact;
    }

    private OpportunityRequest request(UUID companyId, UUID contactId, OpportunityStage stage) {
        return new OpportunityRequest(" New deal ", null, null, null, stage, null, companyId, contactId);
    }

    @Test
    void anotherOrganizationCannotReadOpportunityById() {
        UUID opportunityId = UUID.randomUUID();
        UUID otherOrganizationId = UUID.randomUUID();
        when(opportunityRepository.findByIdAndOrganization_IdAndArchivedFalse(opportunityId, otherOrganizationId))
                .thenReturn(Optional.empty());

        assertThrows(OpportunityNotFoundException.class,
                () -> opportunityService.getById(otherOrganizationId, opportunityId));

        verify(opportunityRepository).findByIdAndOrganization_IdAndArchivedFalse(opportunityId, otherOrganizationId);
    }
}
