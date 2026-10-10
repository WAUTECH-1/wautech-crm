package com.wautech.crm.opportunity.service;

import com.wautech.crm.audit.AuditedMutation;
import com.wautech.crm.company.entity.Company;
import com.wautech.crm.company.repository.CompanyRepository;
import com.wautech.crm.company.service.CompanyNotFoundException;
import com.wautech.crm.contact.entity.Contact;
import com.wautech.crm.contact.repository.ContactRepository;
import com.wautech.crm.contact.service.ContactNotFoundException;
import com.wautech.crm.opportunity.dto.OpportunityRequest;
import com.wautech.crm.opportunity.dto.OpportunityResponse;
import com.wautech.crm.opportunity.entity.Opportunity;
import com.wautech.crm.opportunity.entity.OpportunityStage;
import com.wautech.crm.opportunity.repository.OpportunityRepository;
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
public class OpportunityService {
    private final OpportunityRepository opportunityRepository;
    private final CompanyRepository companyRepository;
    private final ContactRepository contactRepository;
    private final OrganizationService organizationService;

    public OpportunityService(OpportunityRepository opportunityRepository, CompanyRepository companyRepository,
                              ContactRepository contactRepository, OrganizationService organizationService) {
        this.opportunityRepository = opportunityRepository;
        this.companyRepository = companyRepository;
        this.contactRepository = contactRepository;
        this.organizationService = organizationService;
    }

    @PreAuthorize("@crmAuthorization.canWrite(#p0)")
    @AuditedMutation(eventType = "OPPORTUNITY_CREATED", targetType = "OPPORTUNITY")
    public OpportunityResponse create(UUID organizationId, OpportunityRequest request) {
        Organization organization = organizationService.requireActiveOrganization(organizationId);
        Company company = findActiveCompany(organizationId, request.companyId());
        Contact contact = findContactForCompany(organizationId, request.contactId(), company);
        Opportunity opportunity = new Opportunity(organization, request.name().trim(), request.description(), request.amount(),
                request.currency(), request.stage(), request.expectedCloseDate(), company, contact);
        return OpportunityResponse.from(opportunityRepository.save(opportunity));
    }

    @Transactional(readOnly = true)
    @PreAuthorize("@crmAuthorization.canView(#p0)")
    public List<OpportunityResponse> listActive(UUID organizationId, UUID companyId, UUID contactId, OpportunityStage stage) {
        organizationService.requireActiveOrganization(organizationId);
        return opportunityRepository.findActive(organizationId, companyId, contactId, stage, null)
                .stream().map(OpportunityResponse::from).toList();
    }

    @Transactional(readOnly = true)
    @PreAuthorize("@crmAuthorization.canView(#p0)")
    public List<OpportunityResponse> listActive(UUID organizationId, UUID companyId, UUID contactId, OpportunityStage stage,
                                                String search, String sortBy, String sortDirection) {
        organizationService.requireActiveOrganization(organizationId);
        List<Opportunity> rows = new ArrayList<>(opportunityRepository.findActive(organizationId, companyId, contactId, stage,
                SearchText.containsPattern(search)));
        ListSort.apply(rows, sortBy, sortDirection, SORT_FIELDS, Opportunity::getId);
        return rows.stream().map(OpportunityResponse::from).toList();
    }

    @Transactional(readOnly = true)
    @PreAuthorize("@crmAuthorization.canView(#p0)")
    public OpportunityResponse getById(UUID organizationId, UUID id) {
        organizationService.requireActiveOrganization(organizationId);
        return OpportunityResponse.from(findActiveOpportunity(organizationId, id));
    }

    @PreAuthorize("@crmAuthorization.canWrite(#p0)")
    @AuditedMutation(eventType = "OPPORTUNITY_UPDATED", targetType = "OPPORTUNITY")
    public OpportunityResponse update(UUID organizationId, UUID id, OpportunityRequest request) {
        organizationService.requireActiveOrganization(organizationId);
        Opportunity opportunity = findActiveOpportunity(organizationId, id);
        Company company = findActiveCompany(organizationId, request.companyId());
        Contact contact = findContactForCompany(organizationId, request.contactId(), company);
        opportunity.update(request.name().trim(), request.description(), request.amount(), request.currency(),
                request.expectedCloseDate(), company, contact);
        return OpportunityResponse.from(opportunityRepository.save(opportunity));
    }

    @PreAuthorize("@crmAuthorization.canWrite(#p0)")
    @AuditedMutation(eventType = "OPPORTUNITY_STAGE_CHANGED", targetType = "OPPORTUNITY")
    public OpportunityResponse changeStage(UUID organizationId, UUID id, OpportunityStage stage) {
        organizationService.requireActiveOrganization(organizationId);
        Opportunity opportunity = findActiveOpportunity(organizationId, id);
        opportunity.changeStage(stage);
        return OpportunityResponse.from(opportunityRepository.save(opportunity));
    }

    @PreAuthorize("@crmAuthorization.canWrite(#p0)")
    @AuditedMutation(eventType = "OPPORTUNITY_ARCHIVED", targetType = "OPPORTUNITY")
    public void archive(UUID organizationId, UUID id) {
        organizationService.requireActiveOrganization(organizationId);
        Opportunity opportunity = findActiveOpportunity(organizationId, id);
        opportunity.archive();
        opportunityRepository.save(opportunity);
    }

    private Company findActiveCompany(UUID organizationId, UUID id) {
        return companyRepository.findByIdAndOrganization_IdAndArchivedFalse(id, organizationId)
                .orElseThrow(() -> new CompanyNotFoundException(id));
    }

    private Contact findContactForCompany(UUID organizationId, UUID contactId, Company company) {
        if (contactId == null) return null;
        Contact contact = contactRepository.findByIdAndOrganization_IdAndArchivedFalse(contactId, organizationId)
                .orElseThrow(() -> new ContactNotFoundException(contactId));
        if (!contact.getCompany().getId().equals(company.getId())) {
            throw new OpportunityContactCompanyMismatchException(contactId, company.getId());
        }
        return contact;
    }

    private Opportunity findActiveOpportunity(UUID organizationId, UUID id) {
        return opportunityRepository.findByIdAndOrganization_IdAndArchivedFalse(id, organizationId)
                .orElseThrow(() -> new OpportunityNotFoundException(id));
    }

    private static final Map<String, Function<Opportunity, Comparable<?>>> SORT_FIELDS = Map.ofEntries(
            Map.entry("id", Opportunity::getId), Map.entry("name", Opportunity::getName),
            Map.entry("amount", Opportunity::getAmount), Map.entry("currency", Opportunity::getCurrency),
            Map.entry("stage", o -> o.getStage().name()), Map.entry("expectedCloseDate", Opportunity::getExpectedCloseDate),
            Map.entry("createdAt", Opportunity::getCreatedAt), Map.entry("updatedAt", Opportunity::getUpdatedAt));
}
