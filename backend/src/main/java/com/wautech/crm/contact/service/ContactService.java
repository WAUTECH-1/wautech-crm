package com.wautech.crm.contact.service;

import com.wautech.crm.company.entity.Company;
import com.wautech.crm.company.repository.CompanyRepository;
import com.wautech.crm.company.service.CompanyNotFoundException;
import com.wautech.crm.contact.dto.ContactRequest;
import com.wautech.crm.contact.dto.ContactResponse;
import com.wautech.crm.contact.entity.Contact;
import com.wautech.crm.contact.repository.ContactRepository;
import com.wautech.crm.organization.entity.Organization;
import com.wautech.crm.organization.service.OrganizationService;
import com.wautech.crm.platform.search.ListSort;
import com.wautech.crm.platform.search.SearchText;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.ArrayList;
import java.util.UUID;
import java.util.Map;
import java.util.function.Function;

@Service
@Transactional
public class ContactService {
    private final ContactRepository contactRepository;
    private final CompanyRepository companyRepository;
    private final OrganizationService organizationService;

    public ContactService(ContactRepository contactRepository, CompanyRepository companyRepository,
                          OrganizationService organizationService) {
        this.contactRepository = contactRepository;
        this.companyRepository = companyRepository;
        this.organizationService = organizationService;
    }

    @PreAuthorize("@crmAuthorization.canWrite(#p0)")
    public ContactResponse create(UUID organizationId, ContactRequest request) {
        Organization organization = organizationService.requireActiveOrganization(organizationId);
        Company company = findActiveCompany(organizationId, request.companyId());
        Contact contact = new Contact(organization, company, request.firstName().trim(), request.lastName().trim(),
                request.email(), request.phone(), request.jobTitle(), request.resolvedStatus());
        return ContactResponse.from(contactRepository.save(contact));
    }

    @Transactional(readOnly = true)
    @PreAuthorize("@crmAuthorization.canView(#p0)")
    public List<ContactResponse> listActive(UUID organizationId, UUID companyId) {
        organizationService.requireActiveOrganization(organizationId);
        List<Contact> contacts;
        if (companyId == null) contacts = contactRepository.findAllByOrganization_IdAndArchivedFalseOrderByCreatedAtDesc(organizationId);
        else {
            findActiveCompany(organizationId, companyId);
            contacts = contactRepository.findAllByOrganization_IdAndCompany_IdAndArchivedFalseOrderByCreatedAtDesc(organizationId, companyId);
        }
        return contacts.stream().map(ContactResponse::from).toList();
    }

    @Transactional(readOnly = true)
    @PreAuthorize("@crmAuthorization.canView(#p0)")
    public List<ContactResponse> listActive(UUID organizationId, UUID companyId, String search, String sortBy, String sortDirection) {
        organizationService.requireActiveOrganization(organizationId);
        List<Contact> contacts = new ArrayList<>(contactRepository.findActive(organizationId, companyId, SearchText.containsPattern(search)));
        ListSort.apply(contacts, sortBy, sortDirection, SORT_FIELDS, Contact::getId);
        return contacts.stream().map(ContactResponse::from).toList();
    }

    @Transactional(readOnly = true)
    @PreAuthorize("@crmAuthorization.canView(#p0)")
    public ContactResponse getById(UUID organizationId, UUID id) {
        organizationService.requireActiveOrganization(organizationId);
        return ContactResponse.from(findActiveContact(organizationId, id));
    }

    @PreAuthorize("@crmAuthorization.canWrite(#p0)")
    public ContactResponse update(UUID organizationId, UUID id, ContactRequest request) {
        organizationService.requireActiveOrganization(organizationId);
        Contact contact = findActiveContact(organizationId, id);
        Company company = findActiveCompany(organizationId, request.companyId());
        contact.update(company, request.firstName().trim(), request.lastName().trim(), request.email(),
                request.phone(), request.jobTitle(), request.resolvedStatus());
        return ContactResponse.from(contactRepository.save(contact));
    }

    @PreAuthorize("@crmAuthorization.canWrite(#p0)")
    public void archive(UUID organizationId, UUID id) {
        organizationService.requireActiveOrganization(organizationId);
        Contact contact = findActiveContact(organizationId, id);
        contact.archive();
        contactRepository.save(contact);
    }

    private Company findActiveCompany(UUID organizationId, UUID id) {
        return companyRepository.findByIdAndOrganization_IdAndArchivedFalse(id, organizationId)
                .orElseThrow(() -> new CompanyNotFoundException(id));
    }

    private Contact findActiveContact(UUID organizationId, UUID id) {
        return contactRepository.findByIdAndOrganization_IdAndArchivedFalse(id, organizationId)
                .orElseThrow(() -> new ContactNotFoundException(id));
    }

    private static final Map<String, Function<Contact, Comparable<?>>> SORT_FIELDS = Map.ofEntries(
            Map.entry("id", Contact::getId), Map.entry("firstName", Contact::getFirstName),
            Map.entry("lastName", Contact::getLastName), Map.entry("email", Contact::getEmail),
            Map.entry("phone", Contact::getPhone), Map.entry("jobTitle", Contact::getJobTitle),
            Map.entry("status", Contact::getStatus),
            Map.entry("createdAt", Contact::getCreatedAt), Map.entry("updatedAt", Contact::getUpdatedAt));
}
