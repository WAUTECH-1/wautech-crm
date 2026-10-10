package com.wautech.crm.organization.service;

import com.wautech.crm.organization.dto.OrganizationRequest;
import com.wautech.crm.organization.dto.OrganizationResponse;
import com.wautech.crm.organization.entity.Organization;
import com.wautech.crm.organization.repository.OrganizationRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.security.access.prepost.PreAuthorize;

import java.util.UUID;

@Service
@Transactional
public class OrganizationService {
    private final OrganizationRepository repository;

    public OrganizationService(OrganizationRepository repository) {
        this.repository = repository;
    }

    public OrganizationResponse create(OrganizationRequest request) {
        return OrganizationResponse.from(repository.save(new Organization(request.name().trim())));
    }

    @Transactional(readOnly = true)
    @PreAuthorize("@crmAuthorization.canManageOrganization(#p0)")
    public OrganizationResponse getById(UUID id) {
        return OrganizationResponse.from(requireActiveOrganization(id));
    }

    @PreAuthorize("@crmAuthorization.canManageOrganization(#p0)")
    public OrganizationResponse update(UUID id, OrganizationRequest request) {
        Organization organization = requireActiveOrganization(id);
        organization.update(request.name().trim());
        return OrganizationResponse.from(repository.save(organization));
    }

    @PreAuthorize("@crmAuthorization.canManageOrganization(#p0)")
    public void archive(UUID id) {
        Organization organization = requireActiveOrganization(id);
        organization.archive();
        repository.save(organization);
    }

    public Organization requireActiveOrganization(UUID id) {
        return repository.findByIdAndArchivedFalse(id).orElseThrow(() -> new OrganizationNotFoundException(id));
    }

    public Organization lockActiveOrganization(UUID id) {
        return repository.lockByIdAndArchivedFalse(id).orElseThrow(() -> new OrganizationNotFoundException(id));
    }
}
