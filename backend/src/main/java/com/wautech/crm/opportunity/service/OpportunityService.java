package com.wautech.crm.opportunity.service;

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
public class OpportunityService {
    private final OpportunityRepository opportunityRepository;
    private final CompanyRepository companyRepository;
    private final ContactRepository contactRepository;

    public OpportunityService(OpportunityRepository opportunityRepository, CompanyRepository companyRepository,
                              ContactRepository contactRepository) {
        this.opportunityRepository = opportunityRepository;
        this.companyRepository = companyRepository;
        this.contactRepository = contactRepository;
    }

    public OpportunityResponse create(OpportunityRequest request) {
        Company company = findActiveCompany(request.companyId());
        Contact contact = findContactForCompany(request.contactId(), company);
        Opportunity opportunity = new Opportunity(request.name().trim(), request.description(), request.amount(),
                request.currency(), request.stage(), request.expectedCloseDate(), company, contact);
        return OpportunityResponse.from(opportunityRepository.save(opportunity));
    }

    @Transactional(readOnly = true)
    public List<OpportunityResponse> listActive(UUID companyId, UUID contactId, OpportunityStage stage) {
        return opportunityRepository.findActive(companyId, contactId, stage)
                .stream().map(OpportunityResponse::from).toList();
    }

    @Transactional(readOnly = true)
    public List<OpportunityResponse> listActive(UUID companyId, UUID contactId, OpportunityStage stage,
                                                String search, String sortBy, String sortDirection) {
        List<Opportunity> rows = new ArrayList<>(opportunityRepository.findActive(companyId, contactId, stage,
                SearchText.containsPattern(search)));
        ListSort.apply(rows, sortBy, sortDirection, SORT_FIELDS, Opportunity::getId);
        return rows.stream().map(OpportunityResponse::from).toList();
    }

    @Transactional(readOnly = true)
    public OpportunityResponse getById(UUID id) {
        return OpportunityResponse.from(findActiveOpportunity(id));
    }

    public OpportunityResponse update(UUID id, OpportunityRequest request) {
        Opportunity opportunity = findActiveOpportunity(id);
        Company company = findActiveCompany(request.companyId());
        Contact contact = findContactForCompany(request.contactId(), company);
        opportunity.update(request.name().trim(), request.description(), request.amount(), request.currency(),
                request.expectedCloseDate(), company, contact);
        return OpportunityResponse.from(opportunityRepository.save(opportunity));
    }

    public OpportunityResponse changeStage(UUID id, OpportunityStage stage) {
        Opportunity opportunity = findActiveOpportunity(id);
        opportunity.changeStage(stage);
        return OpportunityResponse.from(opportunityRepository.save(opportunity));
    }

    public void archive(UUID id) {
        Opportunity opportunity = findActiveOpportunity(id);
        opportunity.archive();
        opportunityRepository.save(opportunity);
    }

    private Company findActiveCompany(UUID id) {
        return companyRepository.findByIdAndArchivedFalse(id).orElseThrow(() -> new CompanyNotFoundException(id));
    }

    private Contact findContactForCompany(UUID contactId, Company company) {
        if (contactId == null) return null;
        Contact contact = contactRepository.findByIdAndArchivedFalse(contactId)
                .orElseThrow(() -> new ContactNotFoundException(contactId));
        if (!contact.getCompany().getId().equals(company.getId())) {
            throw new OpportunityContactCompanyMismatchException(contactId, company.getId());
        }
        return contact;
    }

    private Opportunity findActiveOpportunity(UUID id) {
        return opportunityRepository.findByIdAndArchivedFalse(id)
                .orElseThrow(() -> new OpportunityNotFoundException(id));
    }

    private static final Map<String, Function<Opportunity, Comparable<?>>> SORT_FIELDS = Map.ofEntries(
            Map.entry("id", Opportunity::getId), Map.entry("name", Opportunity::getName),
            Map.entry("amount", Opportunity::getAmount), Map.entry("currency", Opportunity::getCurrency),
            Map.entry("stage", o -> o.getStage().name()), Map.entry("expectedCloseDate", Opportunity::getExpectedCloseDate),
            Map.entry("createdAt", Opportunity::getCreatedAt), Map.entry("updatedAt", Opportunity::getUpdatedAt));
}
