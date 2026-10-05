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
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@Transactional
public class ActivityService {
    private final ActivityRepository activityRepository;
    private final CompanyRepository companyRepository;
    private final ContactRepository contactRepository;
    private final LeadRepository leadRepository;
    private final OpportunityRepository opportunityRepository;

    public ActivityService(ActivityRepository activityRepository, CompanyRepository companyRepository,
                           ContactRepository contactRepository, LeadRepository leadRepository,
                           OpportunityRepository opportunityRepository) {
        this.activityRepository = activityRepository;
        this.companyRepository = companyRepository;
        this.contactRepository = contactRepository;
        this.leadRepository = leadRepository;
        this.opportunityRepository = opportunityRepository;
    }

    public ActivityResponse create(ActivityRequest request) {
        ParentRecords parents = findParents(request);
        Activity activity = new Activity(parents.company(), parents.contact(), parents.lead(), parents.opportunity(),
                request.type(), request.subject().trim(), request.description(), request.occurredAt());
        return ActivityResponse.from(activityRepository.save(activity));
    }

    @Transactional(readOnly = true)
    public List<ActivityResponse> listActive(UUID companyId, UUID contactId, UUID leadId,
                                             UUID opportunityId, ActivityType type) {
        return activityRepository.findActive(companyId, contactId, leadId, opportunityId, type)
                .stream().map(ActivityResponse::from).toList();
    }

    @Transactional(readOnly = true)
    public ActivityResponse getById(UUID id) {
        return ActivityResponse.from(findActiveActivity(id));
    }

    public ActivityResponse update(UUID id, ActivityRequest request) {
        Activity activity = findActiveActivity(id);
        ParentRecords parents = findParents(request);
        activity.update(parents.company(), parents.contact(), parents.lead(), parents.opportunity(), request.type(),
                request.subject().trim(), request.description(), request.occurredAt());
        return ActivityResponse.from(activityRepository.save(activity));
    }

    public void archive(UUID id) {
        Activity activity = findActiveActivity(id);
        activity.archive();
        activityRepository.save(activity);
    }

    private ParentRecords findParents(ActivityRequest request) {
        Company company = request.companyId() == null ? null : companyRepository
                .findByIdAndArchivedFalse(request.companyId())
                .orElseThrow(() -> new CompanyNotFoundException(request.companyId()));
        Contact contact = request.contactId() == null ? null : contactRepository
                .findByIdAndArchivedFalse(request.contactId())
                .orElseThrow(() -> new ContactNotFoundException(request.contactId()));
        Lead lead = request.leadId() == null ? null : leadRepository
                .findByIdAndArchivedFalse(request.leadId())
                .orElseThrow(() -> new LeadNotFoundException(request.leadId()));
        Opportunity opportunity = request.opportunityId() == null ? null : opportunityRepository
                .findByIdAndArchivedFalse(request.opportunityId())
                .orElseThrow(() -> new OpportunityNotFoundException(request.opportunityId()));
        return new ParentRecords(company, contact, lead, opportunity);
    }

    private Activity findActiveActivity(UUID id) {
        return activityRepository.findByIdAndArchivedFalse(id)
                .orElseThrow(() -> new ActivityNotFoundException(id));
    }

    private record ParentRecords(Company company, Contact contact, Lead lead, Opportunity opportunity) {
    }
}
