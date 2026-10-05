package com.wautech.crm.lead.service;

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
    @InjectMocks private LeadService leadService;

    @Test
    void createsLeadWithoutCompanyAndDefaultsToNew() {
        when(leadRepository.save(any(Lead.class))).thenAnswer(invocation -> invocation.getArgument(0));

        LeadResponse response = leadService.create(request(null));

        assertNull(response.companyId());
        assertEquals(LeadStatus.NEW, response.status());
        assertEquals("Ada", response.firstName());
    }

    @Test
    void createsLeadWithActiveCompany() {
        UUID companyId = UUID.randomUUID();
        Company company = company(companyId);
        when(companyRepository.findByIdAndArchivedFalse(companyId)).thenReturn(Optional.of(company));
        when(leadRepository.save(any(Lead.class))).thenAnswer(invocation -> invocation.getArgument(0));

        LeadResponse response = leadService.create(request(companyId));

        assertEquals(companyId, response.companyId());
        assertEquals(LeadStatus.NEW, response.status());
    }

    @Test
    void rejectsNonexistentCompany() {
        UUID companyId = UUID.randomUUID();
        when(companyRepository.findByIdAndArchivedFalse(companyId)).thenReturn(Optional.empty());

        assertThrows(CompanyNotFoundException.class, () -> leadService.create(request(companyId)));
        verify(leadRepository, never()).save(any());
    }

    @Test
    void rejectsArchivedCompanyAssignment() {
        UUID archivedCompanyId = UUID.randomUUID();
        when(companyRepository.findByIdAndArchivedFalse(archivedCompanyId)).thenReturn(Optional.empty());

        assertThrows(CompanyNotFoundException.class, () -> leadService.create(request(archivedCompanyId)));
        verify(leadRepository, never()).save(any());
    }

    @Test
    void listUsesActiveRepositoryMethodsForAllFilterCombinations() {
        UUID companyId = UUID.randomUUID();
        when(leadRepository.findAllByArchivedFalseOrderByCreatedAtDesc()).thenReturn(List.of());
        when(leadRepository.findAllByArchivedFalseAndStatusOrderByCreatedAtDesc(LeadStatus.NEW)).thenReturn(List.of());
        when(leadRepository.findAllByCompany_IdAndArchivedFalseOrderByCreatedAtDesc(companyId)).thenReturn(List.of());
        when(leadRepository.findAllByCompany_IdAndArchivedFalseAndStatusOrderByCreatedAtDesc(companyId, LeadStatus.NEW))
                .thenReturn(List.of());

        assertTrue(leadService.listActive(null, null).isEmpty());
        assertTrue(leadService.listActive(LeadStatus.NEW, null).isEmpty());
        assertTrue(leadService.listActive(null, companyId).isEmpty());
        assertTrue(leadService.listActive(LeadStatus.NEW, companyId).isEmpty());

        verify(leadRepository).findAllByArchivedFalseOrderByCreatedAtDesc();
        verify(leadRepository).findAllByArchivedFalseAndStatusOrderByCreatedAtDesc(LeadStatus.NEW);
        verify(leadRepository).findAllByCompany_IdAndArchivedFalseOrderByCreatedAtDesc(companyId);
        verify(leadRepository).findAllByCompany_IdAndArchivedFalseAndStatusOrderByCreatedAtDesc(companyId, LeadStatus.NEW);
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
        Lead lead = new Lead(null, "Old", "Name", null, null, null);
        when(leadRepository.findByIdAndArchivedFalse(id)).thenReturn(Optional.of(lead));
        when(leadRepository.save(lead)).thenReturn(lead);

        LeadResponse response = leadService.update(id, request(null));

        assertEquals(LeadStatus.NEW, response.status());
    }

    @Test
    void missingOrArchivedLeadReturnsNotFound() {
        UUID id = UUID.randomUUID();
        when(leadRepository.findByIdAndArchivedFalse(id)).thenReturn(Optional.empty());

        assertThrows(LeadNotFoundException.class, () -> leadService.getById(id));
        assertThrows(LeadNotFoundException.class, () -> leadService.archive(id));
    }

    @Test
    void archiveSetsSoftDeleteFlag() {
        UUID id = UUID.randomUUID();
        Lead lead = new Lead(null, "Ada", "Lovelace", null, null, null);
        when(leadRepository.findByIdAndArchivedFalse(id)).thenReturn(Optional.of(lead));
        when(leadRepository.save(lead)).thenReturn(lead);

        leadService.archive(id);

        assertTrue(lead.isArchived());
        assertNotNull(lead.getUpdatedAt());
    }

    private void assertTransition(LeadStatus current, LeadStatus next) {
        UUID id = UUID.randomUUID();
        Lead lead = new Lead(null, "Ada", "Lovelace", null, null, null);
        for (int i = 0; i < (current == LeadStatus.CONTACTED ? 1 : 0); i++) lead.changeStatus(LeadStatus.CONTACTED);
        when(leadRepository.findByIdAndArchivedFalse(id)).thenReturn(Optional.of(lead));
        when(leadRepository.save(lead)).thenReturn(lead);

        assertEquals(next, leadService.changeStatus(id, next).status());
    }

    private void assertRejectedTransition(LeadStatus current, LeadStatus next) {
        Lead lead = new Lead(null, "Ada", "Lovelace", null, null, null);
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
}
