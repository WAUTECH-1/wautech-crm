package com.wautech.crm.lead.service;

import com.wautech.crm.TestOrganization;
import com.wautech.crm.organization.service.OrganizationService;
import com.wautech.crm.company.entity.Company;
import com.wautech.crm.company.repository.CompanyRepository;
import com.wautech.crm.company.service.CompanyNotFoundException;
import com.wautech.crm.lead.dto.LeadRequest;
import com.wautech.crm.lead.dto.LeadResponse;
import com.wautech.crm.lead.entity.IllegalLeadStatusTransitionException;
import com.wautech.crm.lead.entity.Lead;
import com.wautech.crm.lead.entity.LeadStatus;
import com.wautech.crm.lead.repository.LeadRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class LeadServiceTest {
    @Mock private LeadRepository leadRepository;
    @Mock private CompanyRepository companyRepository;
    @Mock private OrganizationService organizationService;
    @InjectMocks private LeadService leadService;

    @Test
    void createsLeadWithoutCompanyAndDefaultsToNew() {
        when(leadRepository.save(any(Lead.class))).thenAnswer(invocation -> invocation.getArgument(0));

        LeadResponse response = leadService.create(TestOrganization.ID, request(null));

        assertNull(response.companyId());
        assertEquals(LeadStatus.NEW, response.status());
        assertEquals("Ada", response.firstName());
    }

    @Test
    void createsLeadWithActiveCompany() {
        UUID companyId = UUID.randomUUID();
        Company company = company(companyId);
        when(companyRepository.findByIdAndOrganization_IdAndArchivedFalse(companyId, TestOrganization.ID)).thenReturn(Optional.of(company));
        when(leadRepository.save(any(Lead.class))).thenAnswer(invocation -> invocation.getArgument(0));

        LeadResponse response = leadService.create(TestOrganization.ID, request(companyId));

        assertEquals(companyId, response.companyId());
        assertEquals(LeadStatus.NEW, response.status());
    }

    @Test
    void rejectsNonexistentCompany() {
        UUID companyId = UUID.randomUUID();
        when(companyRepository.findByIdAndOrganization_IdAndArchivedFalse(companyId, TestOrganization.ID)).thenReturn(Optional.empty());

        assertThrows(CompanyNotFoundException.class, () -> leadService.create(TestOrganization.ID, request(companyId)));
        verify(leadRepository, never()).save(any());
    }

    @Test
    void rejectsArchivedCompanyAssignment() {
        UUID archivedCompanyId = UUID.randomUUID();
        when(companyRepository.findByIdAndOrganization_IdAndArchivedFalse(archivedCompanyId, TestOrganization.ID)).thenReturn(Optional.empty());

        assertThrows(CompanyNotFoundException.class, () -> leadService.create(TestOrganization.ID, request(archivedCompanyId)));
        verify(leadRepository, never()).save(any());
    }

    @Test
    void listUsesActiveRepositoryMethodsForAllFilterCombinations() {
        UUID companyId = UUID.randomUUID();
        when(leadRepository.findAllByOrganization_IdAndArchivedFalseOrderByCreatedAtDesc(TestOrganization.ID)).thenReturn(List.of());
        when(leadRepository.findAllByOrganization_IdAndArchivedFalseAndStatusOrderByCreatedAtDesc(TestOrganization.ID, LeadStatus.NEW)).thenReturn(List.of());
        when(leadRepository.findAllByOrganization_IdAndCompany_IdAndArchivedFalseOrderByCreatedAtDesc(TestOrganization.ID, companyId)).thenReturn(List.of());
        when(leadRepository.findAllByOrganization_IdAndCompany_IdAndArchivedFalseAndStatusOrderByCreatedAtDesc(TestOrganization.ID, companyId, LeadStatus.NEW))
                .thenReturn(List.of());

        assertTrue(leadService.listActive(TestOrganization.ID, null, null).isEmpty());
        assertTrue(leadService.listActive(TestOrganization.ID, LeadStatus.NEW, null).isEmpty());
        assertTrue(leadService.listActive(TestOrganization.ID, null, companyId).isEmpty());
        assertTrue(leadService.listActive(TestOrganization.ID, LeadStatus.NEW, companyId).isEmpty());

        verify(leadRepository).findAllByOrganization_IdAndArchivedFalseOrderByCreatedAtDesc(TestOrganization.ID);
        verify(leadRepository).findAllByOrganization_IdAndArchivedFalseAndStatusOrderByCreatedAtDesc(TestOrganization.ID, LeadStatus.NEW);
        verify(leadRepository).findAllByOrganization_IdAndCompany_IdAndArchivedFalseOrderByCreatedAtDesc(TestOrganization.ID, companyId);
        verify(leadRepository).findAllByOrganization_IdAndCompany_IdAndArchivedFalseAndStatusOrderByCreatedAtDesc(TestOrganization.ID, companyId, LeadStatus.NEW);
    }

    @ParameterizedTest
    @EnumSource(value = LeadStatus.class, names = {"CONTACTED", "QUALIFIED", "DISQUALIFIED"})
    void allowsEveryTransitionFromNew(LeadStatus nextStatus) {
        assertTransition(LeadStatus.NEW, nextStatus);
    }

    @ParameterizedTest
    @EnumSource(value = LeadStatus.class, names = {"QUALIFIED", "DISQUALIFIED"})
    void allowsEveryTransitionFromContacted(LeadStatus nextStatus) {
        assertTransition(LeadStatus.CONTACTED, nextStatus);
    }

    @ParameterizedTest
    @EnumSource(value = LeadStatus.class, names = {"NEW"})
    void rejectsIllegalTransitionsFromNew(LeadStatus nextStatus) {
        assertRejectedTransition(LeadStatus.NEW, nextStatus);
    }

    @ParameterizedTest
    @EnumSource(LeadStatus.class)
    void qualifiedAndDisqualifiedAreTerminal(LeadStatus nextStatus) {
        assertRejectedTransition(LeadStatus.QUALIFIED, nextStatus);
        assertRejectedTransition(LeadStatus.DISQUALIFIED, nextStatus);
    }

    @Test
    void putStyleUpdateCannotChangeLeadStatus() {
        UUID id = UUID.randomUUID();
        Lead lead = new Lead(new com.wautech.crm.organization.entity.Organization("Test Organization"), null, "Old", "Name", null, null, null);
        when(leadRepository.findByIdAndOrganization_IdAndArchivedFalse(id, TestOrganization.ID)).thenReturn(Optional.of(lead));
        when(leadRepository.save(lead)).thenReturn(lead);

        LeadResponse response = leadService.update(TestOrganization.ID, id, request(null));

        assertEquals(LeadStatus.NEW, response.status());
    }

    @Test
    void missingOrArchivedLeadReturnsNotFound() {
        UUID id = UUID.randomUUID();
        when(leadRepository.findByIdAndOrganization_IdAndArchivedFalse(id, TestOrganization.ID)).thenReturn(Optional.empty());

        assertThrows(LeadNotFoundException.class, () -> leadService.getById(TestOrganization.ID, id));
        assertThrows(LeadNotFoundException.class, () -> leadService.archive(TestOrganization.ID, id));
    }

    @Test
    void archiveSetsSoftDeleteFlag() {
        UUID id = UUID.randomUUID();
        Lead lead = new Lead(new com.wautech.crm.organization.entity.Organization("Test Organization"), null, "Ada", "Lovelace", null, null, null);
        when(leadRepository.findByIdAndOrganization_IdAndArchivedFalse(id, TestOrganization.ID)).thenReturn(Optional.of(lead));
        when(leadRepository.save(lead)).thenReturn(lead);

        leadService.archive(TestOrganization.ID, id);

        assertTrue(lead.isArchived());
        assertNotNull(lead.getUpdatedAt());
    }

    private void assertTransition(LeadStatus current, LeadStatus next) {
        UUID id = UUID.randomUUID();
        Lead lead = new Lead(new com.wautech.crm.organization.entity.Organization("Test Organization"), null, "Ada", "Lovelace", null, null, null);
        for (int i = 0; i < (current == LeadStatus.CONTACTED ? 1 : 0); i++) lead.changeStatus(LeadStatus.CONTACTED);
        when(leadRepository.findByIdAndOrganization_IdAndArchivedFalse(id, TestOrganization.ID)).thenReturn(Optional.of(lead));
        when(leadRepository.save(lead)).thenReturn(lead);

        assertEquals(next, leadService.changeStatus(TestOrganization.ID, id, next).status());
    }

    private void assertRejectedTransition(LeadStatus current, LeadStatus next) {
        Lead lead = new Lead(new com.wautech.crm.organization.entity.Organization("Test Organization"), null, "Ada", "Lovelace", null, null, null);
        if (current == LeadStatus.CONTACTED) lead.changeStatus(LeadStatus.CONTACTED);
        else if (current == LeadStatus.QUALIFIED || current == LeadStatus.DISQUALIFIED) lead.changeStatus(current);
        assertThrows(IllegalLeadStatusTransitionException.class, () -> lead.changeStatus(next));
    }

    private Company company(UUID id) {
        Company company = mock(Company.class);
        when(company.getId()).thenReturn(id);
        return company;
    }

    private LeadRequest request(UUID companyId) {
        return new LeadRequest(companyId, " Ada ", " Lovelace ", "ada@example.com", null, null);
    }

    @Test
    void anotherOrganizationCannotReadLeadById() {
        UUID leadId = UUID.randomUUID();
        UUID otherOrganizationId = UUID.randomUUID();
        when(leadRepository.findByIdAndOrganization_IdAndArchivedFalse(leadId, otherOrganizationId))
                .thenReturn(Optional.empty());

        assertThrows(LeadNotFoundException.class, () -> leadService.getById(otherOrganizationId, leadId));

        verify(leadRepository).findByIdAndOrganization_IdAndArchivedFalse(leadId, otherOrganizationId);
    }
}
