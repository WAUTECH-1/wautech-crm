package com.wautech.crm.contact.service;

import com.wautech.crm.TestOrganization;
import com.wautech.crm.organization.service.OrganizationService;
import com.wautech.crm.company.entity.Company;
import com.wautech.crm.company.repository.CompanyRepository;
import com.wautech.crm.company.service.CompanyNotFoundException;
import com.wautech.crm.contact.dto.ContactRequest;
import com.wautech.crm.contact.dto.ContactResponse;
import com.wautech.crm.contact.entity.Contact;
import com.wautech.crm.contact.repository.ContactRepository;
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
class ContactServiceTest {
    @Mock
    private ContactRepository contactRepository;

    @Mock
    private CompanyRepository companyRepository;

    @Mock private OrganizationService organizationService;
    @InjectMocks
    private ContactService contactService;

    @Test
    void createTrimsNamesAndDefaultsStatus() {
        UUID companyId = UUID.randomUUID();
        Company company = company(companyId);
        when(companyRepository.findByIdAndOrganization_IdAndArchivedFalse(companyId, TestOrganization.ID)).thenReturn(Optional.of(company));
        when(contactRepository.save(any(Contact.class))).thenAnswer(invocation -> invocation.getArgument(0));

        ContactResponse response = contactService.create(TestOrganization.ID, request(companyId, " Ada ", " Lovelace ", null, null));

        assertEquals(companyId, response.companyId());
        assertEquals("Ada", response.firstName());
        assertEquals("Lovelace", response.lastName());
        assertEquals("ACTIVE", response.status());
        assertFalse(response.archived());
        verify(contactRepository).save(any(Contact.class));
    }

    @Test
    void createRejectsMissingOrArchivedCompany() {
        UUID companyId = UUID.randomUUID();
        when(companyRepository.findByIdAndOrganization_IdAndArchivedFalse(companyId, TestOrganization.ID)).thenReturn(Optional.empty());

        assertThrows(CompanyNotFoundException.class,
                () -> contactService.create(TestOrganization.ID, request(companyId, "Ada", "Lovelace", null, null)));
        verifyNoInteractions(contactRepository);
    }

    @Test
    void listReturnsOnlyActiveContacts() {
        UUID companyId = UUID.randomUUID();
        Contact contact = new Contact(new com.wautech.crm.organization.entity.Organization("Test Organization"), company(companyId), "Ada", "Lovelace", null, null, null, "ACTIVE");
        when(contactRepository.findAllByOrganization_IdAndArchivedFalseOrderByCreatedAtDesc(TestOrganization.ID))
                .thenReturn(List.of(contact));

        List<ContactResponse> response = contactService.listActive(TestOrganization.ID, null);

        assertEquals(1, response.size());
        assertEquals("Ada", response.getFirst().firstName());
        verify(contactRepository).findAllByOrganization_IdAndArchivedFalseOrderByCreatedAtDesc(TestOrganization.ID);
    }

    @Test
    void listCanFilterByCompanyAndRejectsMissingCompany() {
        UUID companyId = UUID.randomUUID();
        Company company = new Company(new com.wautech.crm.organization.entity.Organization("Test Organization"), "Acme", null, null, null, null, "ACTIVE");
        when(companyRepository.findByIdAndOrganization_IdAndArchivedFalse(companyId, TestOrganization.ID)).thenReturn(Optional.of(company));
        when(contactRepository.findAllByOrganization_IdAndCompany_IdAndArchivedFalseOrderByCreatedAtDesc(TestOrganization.ID, companyId)).thenReturn(List.of());

        assertTrue(contactService.listActive(TestOrganization.ID, companyId).isEmpty());
        verify(contactRepository).findAllByOrganization_IdAndCompany_IdAndArchivedFalseOrderByCreatedAtDesc(TestOrganization.ID, companyId);

        UUID missingCompanyId = UUID.randomUUID();
        when(companyRepository.findByIdAndOrganization_IdAndArchivedFalse(missingCompanyId, TestOrganization.ID)).thenReturn(Optional.empty());
        assertThrows(CompanyNotFoundException.class, () -> contactService.listActive(TestOrganization.ID, missingCompanyId));
    }

    @Test
    void getByIdRejectsMissingOrArchivedContact() {
        UUID id = UUID.randomUUID();
        when(contactRepository.findByIdAndOrganization_IdAndArchivedFalse(id, TestOrganization.ID)).thenReturn(Optional.empty());

        assertThrows(ContactNotFoundException.class, () -> contactService.getById(TestOrganization.ID, id));
    }

    @Test
    void updateChangesContactFieldsAndCompany() {
        UUID id = UUID.randomUUID();
        UUID companyId = UUID.randomUUID();
        Company company = company(companyId);
        Contact contact = new Contact(new com.wautech.crm.organization.entity.Organization("Test Organization"), company, "Old", "Name", null, null, null, "ACTIVE");
        when(contactRepository.findByIdAndOrganization_IdAndArchivedFalse(id, TestOrganization.ID)).thenReturn(Optional.of(contact));
        when(companyRepository.findByIdAndOrganization_IdAndArchivedFalse(companyId, TestOrganization.ID)).thenReturn(Optional.of(company));
        when(contactRepository.save(contact)).thenReturn(contact);

        ContactResponse response = contactService.update(TestOrganization.ID, id,
                request(companyId, "New", "Name", "new@example.com", "PAUSED"));

        assertEquals("New", response.firstName());
        assertEquals("new@example.com", response.email());
        assertEquals("PAUSED", response.status());
        assertNotNull(response.updatedAt());
    }

    @Test
    void updateRejectsArchivedContact() {
        UUID id = UUID.randomUUID();
        when(contactRepository.findByIdAndOrganization_IdAndArchivedFalse(id, TestOrganization.ID)).thenReturn(Optional.empty());

        assertThrows(ContactNotFoundException.class,
                () -> contactService.update(TestOrganization.ID, id, request(UUID.randomUUID(), "Ada", "Lovelace", null, null)));
        verifyNoInteractions(companyRepository);
    }

    @Test
    void archiveSetsSoftDeleteFlag() {
        UUID id = UUID.randomUUID();
        Contact contact = new Contact(new com.wautech.crm.organization.entity.Organization("Test Organization"), new Company(new com.wautech.crm.organization.entity.Organization("Test Organization"), "Acme", null, null, null, null, "ACTIVE"),
                "Ada", "Lovelace", null, null, null, "ACTIVE");
        when(contactRepository.findByIdAndOrganization_IdAndArchivedFalse(id, TestOrganization.ID)).thenReturn(Optional.of(contact));
        when(contactRepository.save(contact)).thenReturn(contact);

        contactService.archive(TestOrganization.ID, id);

        assertTrue(contact.isArchived());
        assertNotNull(contact.getUpdatedAt());
        verify(contactRepository).save(contact);
    }

    private Company company(UUID id) {
        Company company = mock(Company.class);
        when(company.getId()).thenReturn(id);
        return company;
    }

    private ContactRequest request(UUID companyId, String firstName, String lastName, String email, String status) {
        return new ContactRequest(companyId, firstName, lastName, email, null, null, status);
    }

    @Test
    void anotherOrganizationCannotReadContactById() {
        UUID contactId = UUID.randomUUID();
        UUID otherOrganizationId = UUID.randomUUID();
        when(contactRepository.findByIdAndOrganization_IdAndArchivedFalse(contactId, otherOrganizationId))
                .thenReturn(Optional.empty());

        assertThrows(ContactNotFoundException.class, () -> contactService.getById(otherOrganizationId, contactId));

        verify(contactRepository).findByIdAndOrganization_IdAndArchivedFalse(contactId, otherOrganizationId);
    }
}
