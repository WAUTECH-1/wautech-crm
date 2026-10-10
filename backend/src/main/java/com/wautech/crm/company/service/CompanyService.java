package com.wautech.crm.company.service;

import com.wautech.crm.audit.AuditedMutation;
import com.wautech.crm.company.dto.CompanyRequest;
import com.wautech.crm.company.dto.CompanyResponse;
import com.wautech.crm.company.entity.Company;
import com.wautech.crm.company.repository.CompanyRepository;
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
public class CompanyService {
    private final CompanyRepository companyRepository;
    private final OrganizationService organizationService;

    public CompanyService(CompanyRepository companyRepository, OrganizationService organizationService) {
        this.companyRepository = companyRepository;
        this.organizationService = organizationService;
    }

    @PreAuthorize("@crmAuthorization.canWrite(#p0)")
    @AuditedMutation(eventType = "COMPANY_CREATED", targetType = "COMPANY")
    public CompanyResponse create(UUID organizationId, CompanyRequest request) {
        Organization organization = organizationService.requireActiveOrganization(organizationId);
        Company company = new Company(organization, request.name().trim(), request.website(), request.industry(),
                request.phone(), request.email(), request.resolvedStatus());
        return CompanyResponse.from(companyRepository.save(company));
    }

    @Transactional(readOnly = true)
    @PreAuthorize("@crmAuthorization.canView(#p0)")
    public List<CompanyResponse> listActive(UUID organizationId) {
        organizationService.requireActiveOrganization(organizationId);
        return companyRepository.findAllByOrganization_IdAndArchivedFalseOrderByCreatedAtDesc(organizationId).stream()
                .map(CompanyResponse::from).toList();
    }

    @Transactional(readOnly = true)
    @PreAuthorize("@crmAuthorization.canView(#p0)")
    public List<CompanyResponse> listActive(UUID organizationId, String search, String sortBy, String sortDirection) {
        organizationService.requireActiveOrganization(organizationId);
        var rows = new ArrayList<>(companyRepository.findActive(organizationId, SearchText.containsPattern(search)));
        ListSort.apply(rows, sortBy, sortDirection, SORT_FIELDS, Company::getId);
        return rows.stream()
                .map(CompanyResponse::from)
                .toList();
    }

    @Transactional(readOnly = true)
    @PreAuthorize("@crmAuthorization.canView(#p0)")
    public CompanyResponse getById(UUID organizationId, UUID id) {
        organizationService.requireActiveOrganization(organizationId);
        return CompanyResponse.from(findActiveCompany(organizationId, id));
    }

    @PreAuthorize("@crmAuthorization.canWrite(#p0)")
    @AuditedMutation(eventType = "COMPANY_UPDATED", targetType = "COMPANY")
    public CompanyResponse update(UUID organizationId, UUID id, CompanyRequest request) {
        organizationService.requireActiveOrganization(organizationId);
        Company company = findActiveCompany(organizationId, id);
        company.update(request.name().trim(), request.website(), request.industry(), request.phone(),
                request.email(), request.resolvedStatus());
        return CompanyResponse.from(companyRepository.save(company));
    }

    @PreAuthorize("@crmAuthorization.canWrite(#p0)")
    @AuditedMutation(eventType = "COMPANY_ARCHIVED", targetType = "COMPANY")
    public void archive(UUID organizationId, UUID id) {
        organizationService.requireActiveOrganization(organizationId);
        Company company = findActiveCompany(organizationId, id);
        company.archive();
        companyRepository.save(company);
    }

    private Company findActiveCompany(UUID organizationId, UUID id) {
        return companyRepository.findByIdAndOrganization_IdAndArchivedFalse(id, organizationId)
                .orElseThrow(() -> new CompanyNotFoundException(id));
    }

    private static final Map<String, Function<Company, Comparable<?>>> SORT_FIELDS = Map.ofEntries(
            Map.entry("id", Company::getId), Map.entry("name", Company::getName),
            Map.entry("website", Company::getWebsite), Map.entry("industry", Company::getIndustry),
            Map.entry("phone", Company::getPhone), Map.entry("email", Company::getEmail),
            Map.entry("status", Company::getStatus), Map.entry("createdAt", Company::getCreatedAt),
            Map.entry("updatedAt", Company::getUpdatedAt));
}
