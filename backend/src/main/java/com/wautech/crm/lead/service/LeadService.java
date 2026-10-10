package com.wautech.crm.lead.service;

import com.wautech.crm.audit.AuditedMutation;
import com.wautech.crm.company.entity.Company;
import com.wautech.crm.company.repository.CompanyRepository;
import com.wautech.crm.company.service.CompanyNotFoundException;
import com.wautech.crm.lead.dto.LeadRequest;
import com.wautech.crm.lead.dto.LeadResponse;
import com.wautech.crm.lead.entity.Lead;
import com.wautech.crm.lead.entity.LeadStatus;
import com.wautech.crm.lead.repository.LeadRepository;
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
public class LeadService {
    private final LeadRepository leadRepository;
    private final CompanyRepository companyRepository;
    private final OrganizationService organizationService;

    public LeadService(LeadRepository leadRepository, CompanyRepository companyRepository,
                       OrganizationService organizationService) {
        this.leadRepository = leadRepository;
        this.companyRepository = companyRepository;
        this.organizationService = organizationService;
    }

    @PreAuthorize("@crmAuthorization.canWrite(#p0)")
    @AuditedMutation(eventType = "LEAD_CREATED", targetType = "LEAD")
    public LeadResponse create(UUID organizationId, LeadRequest request) {
        Organization organization = organizationService.requireActiveOrganization(organizationId);
        Company company = request.companyId() == null ? null : findActiveCompany(organizationId, request.companyId());
        Lead lead = new Lead(organization, company, request.firstName().trim(), request.lastName().trim(),
                request.email(), request.phone(), request.jobTitle());
        return LeadResponse.from(leadRepository.save(lead));
    }

    @Transactional(readOnly = true)
    @PreAuthorize("@crmAuthorization.canView(#p0)")
    public List<LeadResponse> listActive(UUID organizationId, LeadStatus status, UUID companyId) {
        organizationService.requireActiveOrganization(organizationId);
        List<Lead> leads;
        if (status != null && companyId != null) {
            leads = leadRepository.findAllByOrganization_IdAndCompany_IdAndArchivedFalseAndStatusOrderByCreatedAtDesc(organizationId, companyId, status);
        } else if (status != null) leads = leadRepository.findAllByOrganization_IdAndArchivedFalseAndStatusOrderByCreatedAtDesc(organizationId, status);
        else if (companyId != null) leads = leadRepository.findAllByOrganization_IdAndCompany_IdAndArchivedFalseOrderByCreatedAtDesc(organizationId, companyId);
        else leads = leadRepository.findAllByOrganization_IdAndArchivedFalseOrderByCreatedAtDesc(organizationId);
        return leads.stream().map(LeadResponse::from).toList();
    }

    @Transactional(readOnly = true)
    @PreAuthorize("@crmAuthorization.canView(#p0)")
    public List<LeadResponse> listActive(UUID organizationId, LeadStatus status, UUID companyId, String search,
                                         String sortBy, String sortDirection) {
        organizationService.requireActiveOrganization(organizationId);
        List<Lead> leads = new ArrayList<>(leadRepository.findActive(organizationId, companyId, status, SearchText.containsPattern(search)));
        ListSort.apply(leads, sortBy, sortDirection, SORT_FIELDS, Lead::getId);
        return leads.stream().map(LeadResponse::from).toList();
    }

    @Transactional(readOnly = true)
    @PreAuthorize("@crmAuthorization.canView(#p0)")
    public LeadResponse getById(UUID organizationId, UUID id) {
        organizationService.requireActiveOrganization(organizationId);
        return LeadResponse.from(findActiveLead(organizationId, id));
    }

    @PreAuthorize("@crmAuthorization.canWrite(#p0)")
    @AuditedMutation(eventType = "LEAD_UPDATED", targetType = "LEAD")
    public LeadResponse update(UUID organizationId, UUID id, LeadRequest request) {
        organizationService.requireActiveOrganization(organizationId);
        Lead lead = findActiveLead(organizationId, id);
        Company company = request.companyId() == null ? null : findActiveCompany(organizationId, request.companyId());
        lead.update(company, request.firstName().trim(), request.lastName().trim(), request.email(),
                request.phone(), request.jobTitle());
        return LeadResponse.from(leadRepository.save(lead));
    }

    @PreAuthorize("@crmAuthorization.canWrite(#p0)")
    @AuditedMutation(eventType = "LEAD_STATUS_CHANGED", targetType = "LEAD")
    public LeadResponse changeStatus(UUID organizationId, UUID id, LeadStatus newStatus) {
        organizationService.requireActiveOrganization(organizationId);
        Lead lead = findActiveLead(organizationId, id);
        lead.changeStatus(newStatus);
        return LeadResponse.from(leadRepository.save(lead));
    }

    @PreAuthorize("@crmAuthorization.canWrite(#p0)")
    @AuditedMutation(eventType = "LEAD_ARCHIVED", targetType = "LEAD")
    public void archive(UUID organizationId, UUID id) {
        organizationService.requireActiveOrganization(organizationId);
        Lead lead = findActiveLead(organizationId, id);
        lead.archive();
        leadRepository.save(lead);
    }

    private Company findActiveCompany(UUID organizationId, UUID id) {
        return companyRepository.findByIdAndOrganization_IdAndArchivedFalse(id, organizationId)
                .orElseThrow(() -> new CompanyNotFoundException(id));
    }

    private Lead findActiveLead(UUID organizationId, UUID id) {
        return leadRepository.findByIdAndOrganization_IdAndArchivedFalse(id, organizationId)
                .orElseThrow(() -> new LeadNotFoundException(id));
    }

    private static final Map<String, Function<Lead, Comparable<?>>> SORT_FIELDS = Map.ofEntries(
            Map.entry("id", Lead::getId), Map.entry("firstName", Lead::getFirstName),
            Map.entry("lastName", Lead::getLastName), Map.entry("email", Lead::getEmail),
            Map.entry("phone", Lead::getPhone), Map.entry("jobTitle", Lead::getJobTitle),
            Map.entry("status", l -> l.getStatus().name()),
            Map.entry("createdAt", Lead::getCreatedAt), Map.entry("updatedAt", Lead::getUpdatedAt));
}
