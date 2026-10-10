package com.wautech.crm.company.service;

import com.wautech.crm.TestOrganization;
import com.wautech.crm.organization.service.OrganizationService;
import com.wautech.crm.company.dto.CompanyRequest;
import com.wautech.crm.company.dto.CompanyResponse;
import com.wautech.crm.company.entity.Company;
import com.wautech.crm.company.repository.CompanyRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
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
class CompanyServiceTest {
    @Mock
    private CompanyRepository companyRepository;

    @Mock private OrganizationService organizationService;
    @InjectMocks
    private CompanyService companyService;

    @Test
    void createTrimsNameAndDefaultsStatus() {
        when(companyRepository.save(any(Company.class))).thenAnswer(invocation -> invocation.getArgument(0));

        CompanyResponse response = companyService.create(TestOrganization.ID, request("  Acme  ", null, null));

        assertEquals("Acme", response.name());
        assertEquals("ACTIVE", response.status());
        assertFalse(response.archived());
        verify(companyRepository).save(any(Company.class));
    }

    @Test
    void listReturnsOnlyRepositorySelectedActiveCompanies() {
        when(companyRepository.findAllByOrganization_IdAndArchivedFalseOrderByCreatedAtDesc(TestOrganization.ID))
                .thenReturn(List.of(new Company(new com.wautech.crm.organization.entity.Organization("Test Organization"), "Acme", null, null, null, null, "ACTIVE")));

        List<CompanyResponse> response = companyService.listActive(TestOrganization.ID);

        assertEquals(1, response.size());
        assertEquals("Acme", response.getFirst().name());
        verify(companyRepository).findAllByOrganization_IdAndArchivedFalseOrderByCreatedAtDesc(TestOrganization.ID);
    }

    @Test
    void getByIdThrowsWhenCompanyIsMissing() {
        UUID id = UUID.randomUUID();
        when(companyRepository.findByIdAndOrganization_IdAndArchivedFalse(id, TestOrganization.ID)).thenReturn(Optional.empty());

        assertThrows(CompanyNotFoundException.class, () -> companyService.getById(TestOrganization.ID, id));
    }

    @Test
    void updateChangesCompanyFields() {
        UUID id = UUID.randomUUID();
        Company company = new Company(new com.wautech.crm.organization.entity.Organization("Test Organization"), "Old name", null, null, null, null, "ACTIVE");
        when(companyRepository.findByIdAndOrganization_IdAndArchivedFalse(id, TestOrganization.ID)).thenReturn(Optional.of(company));
        when(companyRepository.save(company)).thenReturn(company);

        CompanyResponse response = companyService.update(TestOrganization.ID, id, request("New name", "Technology", "PAUSED"));

        assertEquals("New name", response.name());
        assertEquals("Technology", response.industry());
        assertEquals("PAUSED", response.status());
        assertNotNull(response.updatedAt());
    }

    @Test
    void archiveSetsSoftDeleteFlag() {
        UUID id = UUID.randomUUID();
        Company company = new Company(new com.wautech.crm.organization.entity.Organization("Test Organization"), "Acme", null, null, null, null, "ACTIVE");
        when(companyRepository.findByIdAndOrganization_IdAndArchivedFalse(id, TestOrganization.ID)).thenReturn(Optional.of(company));
        when(companyRepository.save(company)).thenReturn(company);

        companyService.archive(TestOrganization.ID, id);

        assertTrue(company.isArchived());
        assertNotNull(company.getUpdatedAt());
        verify(companyRepository).save(company);
    }

    private CompanyRequest request(String name, String industry, String status) {
        return new CompanyRequest(name, null, industry, null, null, status);
    }

    @Test
    void anotherOrganizationCannotReadCompanyById() {
        UUID companyId = UUID.randomUUID();
        UUID otherOrganizationId = UUID.randomUUID();
        when(companyRepository.findByIdAndOrganization_IdAndArchivedFalse(companyId, otherOrganizationId))
                .thenReturn(Optional.empty());

        assertThrows(CompanyNotFoundException.class, () -> companyService.getById(otherOrganizationId, companyId));

        verify(companyRepository).findByIdAndOrganization_IdAndArchivedFalse(companyId, otherOrganizationId);
    }
}
