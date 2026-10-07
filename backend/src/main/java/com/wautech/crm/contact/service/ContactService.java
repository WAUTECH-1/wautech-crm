package com.wautech.crm.contact.service;

import com.wautech.crm.company.entity.Company;
import com.wautech.crm.company.repository.CompanyRepository;
import com.wautech.crm.company.service.CompanyNotFoundException;
import com.wautech.crm.contact.dto.ContactRequest;
import com.wautech.crm.contact.dto.ContactResponse;
import com.wautech.crm.contact.entity.Contact;
import com.wautech.crm.contact.repository.ContactRepository;
import com.wautech.crm.platform.search.ListSort;
import com.wautech.crm.platform.search.SearchText;
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

    public ContactService(ContactRepository contactRepository, CompanyRepository companyRepository) {
        this.contactRepository = contactRepository;
        this.companyRepository = companyRepository;
    }

    public ContactResponse create(ContactRequest request) {
        Company company = findActiveCompany(request.companyId());
        Contact contact = new Contact(company, request.firstName().trim(), request.lastName().trim(),
                request.email(), request.phone(), request.jobTitle(), request.resolvedStatus());
        return ContactResponse.from(contactRepository.save(contact));
    }

    @Transactional(readOnly = true)
    public List<ContactResponse> listActive(UUID companyId) {
        List<Contact> contacts;
        if (companyId == null) contacts = contactRepository.findAllByArchivedFalseOrderByCreatedAtDesc();
        else {
            findActiveCompany(companyId);
            contacts = contactRepository.findAllByCompany_IdAndArchivedFalseOrderByCreatedAtDesc(companyId);
        }
        return contacts.stream().map(ContactResponse::from).toList();
    }

    @Transactional(readOnly = true)
    public List<ContactResponse> listActive(UUID companyId, String search, String sortBy, String sortDirection) {
        List<Contact> contacts = new ArrayList<>(contactRepository.findActive(companyId, SearchText.containsPattern(search)));
        ListSort.apply(contacts, sortBy, sortDirection, SORT_FIELDS, Contact::getId);
        return contacts.stream().map(ContactResponse::from).toList();
    }

    @Transactional(readOnly = true)
    public ContactResponse getById(UUID id) {
        return ContactResponse.from(findActiveContact(id));
    }

    public ContactResponse update(UUID id, ContactRequest request) {
        Contact contact = findActiveContact(id);
        Company company = findActiveCompany(request.companyId());
        contact.update(company, request.firstName().trim(), request.lastName().trim(), request.email(),
                request.phone(), request.jobTitle(), request.resolvedStatus());
        return ContactResponse.from(contactRepository.save(contact));
    }

    public void archive(UUID id) {
        Contact contact = findActiveContact(id);
        contact.archive();
        contactRepository.save(contact);
    }

    private Company findActiveCompany(UUID id) {
        return companyRepository.findByIdAndArchivedFalse(id).orElseThrow(() -> new CompanyNotFoundException(id));
    }

    private Contact findActiveContact(UUID id) {
        return contactRepository.findByIdAndArchivedFalse(id).orElseThrow(() -> new ContactNotFoundException(id));
    }

    private static final Map<String, Function<Contact, Comparable<?>>> SORT_FIELDS = Map.ofEntries(
            Map.entry("id", Contact::getId), Map.entry("firstName", Contact::getFirstName),
            Map.entry("lastName", Contact::getLastName), Map.entry("email", Contact::getEmail),
            Map.entry("phone", Contact::getPhone), Map.entry("jobTitle", Contact::getJobTitle),
            Map.entry("status", Contact::getStatus),
            Map.entry("createdAt", Contact::getCreatedAt), Map.entry("updatedAt", Contact::getUpdatedAt));
}
