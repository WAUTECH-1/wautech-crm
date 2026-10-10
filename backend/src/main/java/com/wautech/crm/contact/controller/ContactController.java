package com.wautech.crm.contact.controller;

import com.wautech.crm.contact.dto.ContactRequest;
import com.wautech.crm.contact.dto.ContactResponse;
import com.wautech.crm.contact.service.ContactService;
import com.wautech.crm.platform.tenant.AuthenticatedOrganizationContext;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/contacts")
public class ContactController {
    private final ContactService contactService;
    private final AuthenticatedOrganizationContext organizationContext;

    public ContactController(ContactService contactService, AuthenticatedOrganizationContext organizationContext) {
        this.contactService = contactService;
        this.organizationContext = organizationContext;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ContactResponse create(@Valid @RequestBody ContactRequest request) {
        return contactService.create(organizationContext.requireOrganizationId(), request);
    }

    @GetMapping
    public List<ContactResponse> listActive(@RequestParam(required = false) UUID companyId,
                                            @RequestParam(required = false) String search,
                                            @RequestParam(required = false) String sortBy,
                                            @RequestParam(required = false) String sortDirection) {
        return search == null && sortBy == null && sortDirection == null
                ? contactService.listActive(organizationContext.requireOrganizationId(), companyId)
                : contactService.listActive(organizationContext.requireOrganizationId(), companyId, search, sortBy, sortDirection);
    }

    @GetMapping("/{id}")
    public ContactResponse getById(@PathVariable UUID id) {
        return contactService.getById(organizationContext.requireOrganizationId(), id);
    }

    @PutMapping("/{id}")
    public ContactResponse update(@PathVariable UUID id, @Valid @RequestBody ContactRequest request) {
        return contactService.update(organizationContext.requireOrganizationId(), id, request);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void archive(@PathVariable UUID id) {
        contactService.archive(organizationContext.requireOrganizationId(), id);
    }
}
