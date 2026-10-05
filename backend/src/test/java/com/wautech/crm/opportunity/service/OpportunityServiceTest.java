package com.wautech.crm.opportunity.service;

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
    @InjectMocks private OpportunityService opportunityService;

    @Test
    void createsOpportunityWithCompanyAndDefaultsStage() {
        UUID companyId = UUID.randomUUID();
        Company company = company(companyId);
        when(companyRepository.findByIdAndArchivedFalse(companyId)).thenReturn(Optional.of(company));
        when(opportunityRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        OpportunityResponse result = opportunityService.create(request(companyId, null, null));

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
        when(companyRepository.findByIdAndArchivedFalse(companyId)).thenReturn(Optional.of(company));
        when(contactRepository.findByIdAndArchivedFalse(contactId)).thenReturn(Optional.of(contact));
        when(opportunityRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        OpportunityResponse result = opportunityService.create(request(companyId, contactId, null));

        assertEquals(contactId, result.contactId());
    }

    @Test
    void rejectsMissingAndArchivedCompany() {
        UUID companyId = UUID.randomUUID();
        when(companyRepository.findByIdAndArchivedFalse(companyId)).thenReturn(Optional.empty());

        assertThrows(CompanyNotFoundException.class, () -> opportunityService.create(request(companyId, null, null)));
        verify(opportunityRepository, never()).save(any());
    }

    @Test
    void rejectsMissingAndArchivedContact() {
        UUID companyId = UUID.randomUUID();
        UUID contactId = UUID.randomUUID();
        Company company = new Company("Example", null, null, null, null, "ACTIVE");
        when(companyRepository.findByIdAndArchivedFalse(companyId)).thenReturn(Optional.of(company));
        when(contactRepository.findByIdAndArchivedFalse(contactId)).thenReturn(Optional.empty());

        assertThrows(ContactNotFoundException.class, () -> opportunityService.create(request(companyId, contactId, null)));
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
        when(companyRepository.findByIdAndArchivedFalse(companyId)).thenReturn(Optional.of(company));
        when(contactRepository.findByIdAndArchivedFalse(contactId))
                .thenReturn(Optional.of(contact));

        assertThrows(OpportunityContactCompanyMismatchException.class,
                () -> opportunityService.create(request(companyId, contactId, null)));
        verify(opportunityRepository, never()).save(any());
    }

    @Test
    void retrievesAndUpdatesActiveOpportunityWithoutChangingStageThroughPut() {
        UUID id = UUID.randomUUID();
        Company company = company(UUID.randomUUID());
        Opportunity opportunity = new Opportunity("Old", null, null, null, OpportunityStage.PROPOSAL,
                null, company, null);
        when(opportunityRepository.findByIdAndArchivedFalse(id)).thenReturn(Optional.of(opportunity));
        when(companyRepository.findByIdAndArchivedFalse(company.getId())).thenReturn(Optional.of(company));
        when(opportunityRepository.save(opportunity)).thenReturn(opportunity);

        assertEquals(OpportunityStage.PROPOSAL, opportunityService.getById(id).stage());
        OpportunityResponse result = opportunityService.update(id,
                new OpportunityRequest(" Updated ", null, null, null, OpportunityStage.CLOSED_WON,
                        LocalDate.of(2027, 1, 1), company.getId(), null));

        assertEquals("Updated", result.name());
        assertEquals(OpportunityStage.PROPOSAL, result.stage());
        assertEquals(LocalDate.of(2027, 1, 1), result.expectedCloseDate());
    }

    @Test
    void missingOrArchivedOpportunityIsNotFoundForReadUpdateStageAndArchive() {
        UUID id = UUID.randomUUID();
        when(opportunityRepository.findByIdAndArchivedFalse(id)).thenReturn(Optional.empty());
        OpportunityRequest request = request(UUID.randomUUID(), null, null);

        assertThrows(OpportunityNotFoundException.class, () -> opportunityService.getById(id));
        assertThrows(OpportunityNotFoundException.class, () -> opportunityService.update(id, request));
        assertThrows(OpportunityNotFoundException.class,
                () -> opportunityService.changeStage(id, OpportunityStage.NEEDS_ANALYSIS));
        assertThrows(OpportunityNotFoundException.class, () -> opportunityService.archive(id));
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
        when(opportunityRepository.findActive(companyId, contactId, OpportunityStage.PROPOSAL)).thenReturn(List.of());

        assertTrue(opportunityService.listActive(companyId, contactId, OpportunityStage.PROPOSAL).isEmpty());

        verify(opportunityRepository).findActive(companyId, contactId, OpportunityStage.PROPOSAL);
    }

    @Test
    void archiveSoftDeletesOpportunity() {
        UUID id = UUID.randomUUID();
        Opportunity opportunity = new Opportunity("Deal", null, null, null, null, null, null, null);
        when(opportunityRepository.findByIdAndArchivedFalse(id)).thenReturn(Optional.of(opportunity));
        when(opportunityRepository.save(opportunity)).thenReturn(opportunity);

        opportunityService.archive(id);

        assertTrue(opportunity.isArchived());
        assertNotNull(opportunity.getUpdatedAt());
    }

    private void assertTransition(OpportunityStage current, OpportunityStage next) {
        UUID id = UUID.randomUUID();
        Opportunity opportunity = opportunityAt(current, company(UUID.randomUUID()));
        when(opportunityRepository.findByIdAndArchivedFalse(id)).thenReturn(Optional.of(opportunity));
        when(opportunityRepository.save(opportunity)).thenReturn(opportunity);
        assertEquals(next, opportunityService.changeStage(id, next).stage());
    }

    private void assertRejectedTransition(OpportunityStage current, OpportunityStage next) {
        Opportunity opportunity = opportunityAt(current, null);
        assertThrows(IllegalOpportunityStageTransitionException.class, () -> opportunity.changeStage(next));
    }

    private Opportunity opportunityAt(OpportunityStage stage, Company company) {
        return new Opportunity("Deal", null, null, null, stage, null, company, null);
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
}
