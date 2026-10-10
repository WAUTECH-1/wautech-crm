package com.wautech.crm.activity.service;

import com.wautech.crm.activity.dto.ActivityRequest;
import com.wautech.crm.activity.dto.ActivityResponse;
import com.wautech.crm.activity.entity.Activity;
import com.wautech.crm.activity.entity.ActivityType;
import com.wautech.crm.activity.repository.ActivityRepository;
import com.wautech.crm.company.entity.Company;
import com.wautech.crm.company.repository.CompanyRepository;
import com.wautech.crm.company.service.CompanyNotFoundException;
import com.wautech.crm.contact.entity.Contact;
import com.wautech.crm.contact.repository.ContactRepository;
import com.wautech.crm.contact.service.ContactNotFoundException;
import com.wautech.crm.lead.entity.Lead;
import com.wautech.crm.lead.repository.LeadRepository;
import com.wautech.crm.lead.service.LeadNotFoundException;
import com.wautech.crm.opportunity.entity.Opportunity;
import com.wautech.crm.opportunity.repository.OpportunityRepository;
import com.wautech.crm.opportunity.service.OpportunityNotFoundException;
import com.wautech.crm.platform.search.ListSort;
import com.wautech.crm.platform.search.SearchText;
import com.wautech.crm.organization.entity.Organization;
import com.wautech.crm.organization.service.OrganizationService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.ArrayList;
import java.util.UUID;
import java.util.Map;
import java.util.function.Function;

@Service
@Transactional
public class ActivityService {
    private final ActivityRepository activityRepository;
    private final CompanyRepository companyRepository;
    private final ContactRepository contactRepository;
    private final LeadRepository leadRepository;
    private final OpportunityRepository opportunityRepository;
    private final OrganizationService organizationService;

    public ActivityService(ActivityRepository activityRepository, CompanyRepository companyRepository,
                           ContactRepository contactRepository, LeadRepository leadRepository,
                           OpportunityRepository opportunityRepository, OrganizationService organizationService) {
        this.activityRepository = activityRepository;
        this.companyRepository = companyRepository;
        this.contactRepository = contactRepository;
        this.leadRepository = leadRepository;
        this.opportunityRepository = opportunityRepository;
        this.organizationService = organizationService;
    }

    public ActivityResponse create(UUID organizationId, ActivityRequest request) {
        Organization organization = organizationService.requireActiveOrganization(organizationId);
        ParentRecords parents = findParents(organizationId, request);
        Activity activity = new Activity(organization, parents.company(), parents.contact(), parents.lead(), parents.opportunity(),
                request.type(), request.subject().trim(), request.description(), request.occurredAt());
        return ActivityResponse.from(activityRepository.save(activity));
    }

    @Transactional(readOnly = true)
    public List<ActivityResponse> listActive(UUID organizationId, UUID companyId, UUID contactId, UUID leadId,
                                             UUID opportunityId, ActivityType type) {
        organizationService.requireActiveOrganization(organizationId);
        return activityRepository.findActive(organizationId, companyId, contactId, leadId, opportunityId, type)
                .stream().map(ActivityResponse::from).toList();
    }

    @Transactional(readOnly = true)
    public List<ActivityResponse> listActive(UUID organizationId, UUID companyId, UUID contactId, UUID leadId, UUID opportunityId,
                                             ActivityType type, String search, String sortBy, String sortDirection) {
        organizationService.requireActiveOrganization(organizationId);
        List<Activity> rows = new ArrayList<>(activityRepository.findActive(organizationId, companyId, contactId, leadId, opportunityId, type,
                SearchText.containsPattern(search)));
        ListSort.apply(rows, sortBy, sortDirection, SORT_FIELDS, Activity::getId);
        return rows.stream().map(ActivityResponse::from).toList();
    }

    @Transactional(readOnly = true)
    public ActivityResponse getById(UUID organizationId, UUID id) {
        organizationService.requireActiveOrganization(organizationId);
        return ActivityResponse.from(findActiveActivity(organizationId, id));
    }

    public ActivityResponse update(UUID organizationId, UUID id, ActivityRequest request) {
        organizationService.requireActiveOrganization(organizationId);
        Activity activity = findActiveActivity(organizationId, id);
        ParentRecords parents = findParents(organizationId, request);
        activity.update(parents.company(), parents.contact(), parents.lead(), parents.opportunity(), request.type(),
                request.subject().trim(), request.description(), request.occurredAt());
        return ActivityResponse.from(activityRepository.save(activity));
    }

    public void archive(UUID organizationId, UUID id) {
        organizationService.requireActiveOrganization(organizationId);
        Activity activity = findActiveActivity(organizationId, id);
        activity.archive();
        activityRepository.save(activity);
    }

    private ParentRecords findParents(UUID organizationId, ActivityRequest request) {
        Company company = request.companyId() == null ? null : companyRepository
                .findByIdAndOrganization_IdAndArchivedFalse(request.companyId(), organizationId)
                .orElseThrow(() -> new CompanyNotFoundException(request.companyId()));
        Contact contact = request.contactId() == null ? null : contactRepository
                .findByIdAndOrganization_IdAndArchivedFalse(request.contactId(), organizationId)
                .orElseThrow(() -> new ContactNotFoundException(request.contactId()));
        Lead lead = request.leadId() == null ? null : leadRepository
                .findByIdAndOrganization_IdAndArchivedFalse(request.leadId(), organizationId)
                .orElseThrow(() -> new LeadNotFoundException(request.leadId()));
        Opportunity opportunity = request.opportunityId() == null ? null : opportunityRepository
                .findByIdAndOrganization_IdAndArchivedFalse(request.opportunityId(), organizationId)
                .orElseThrow(() -> new OpportunityNotFoundException(request.opportunityId()));
        return new ParentRecords(company, contact, lead, opportunity);
    }

    private Activity findActiveActivity(UUID organizationId, UUID id) {
        return activityRepository.findByIdAndOrganization_IdAndArchivedFalse(id, organizationId)
                .orElseThrow(() -> new ActivityNotFoundException(id));
    }

    private record ParentRecords(Company company, Contact contact, Lead lead, Opportunity opportunity) {
    }

    private static final Map<String, Function<Activity, Comparable<?>>> SORT_FIELDS = Map.ofEntries(
            Map.entry("id", Activity::getId), Map.entry("type", a -> a.getType().name()),
            Map.entry("subject", Activity::getSubject), Map.entry("occurredAt", Activity::getOccurredAt),
            Map.entry("createdAt", Activity::getCreatedAt), Map.entry("updatedAt", Activity::getUpdatedAt));
}
